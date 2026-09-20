package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.VisualWorkbenchCompatibility;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import java.util.*;

/** Uses recipe assembly on real tables and actual vanilla furnace inventories and server ticks. */
public final class VanillaProcessingAdapter implements ProcessingAdapter {
    private record Binding(RecipeHolder<?> holder, List<Integer> slots) {}
    private final Map<String, Binding> bindings = new HashMap<>();
    @Override public List<CraftRecipe<ItemKey>> recipes(CraftingService.NetworkAccess access) {
        bindings.clear();
        List<CraftRecipe<ItemKey>> result = new ArrayList<>();
        Map<ItemKey, Long> snapshot = access.snapshot();
        Map<net.minecraft.world.item.Item, List<ItemKey>> stockByItem = snapshot.keySet().stream().collect(java.util.stream.Collectors.groupingBy(key -> key.sample().getItem()));
        for (RecipeHolder<?> holder : access.level().getRecipeManager().getRecipes()) {
            try {
            Recipe<?> recipe = holder.value();
            String process = process(recipe);
            if (process == null || recipe.isSpecial()) continue;
            ItemStack output = recipe.getResultItem(access.level().registryAccess());
            if (output.isEmpty() || recipe.getIngredients().isEmpty()) continue;
            List<CraftRecipe.Ingredient<ItemKey>> ingredients = new ArrayList<>();
            List<Integer> slots = new ArrayList<>();
            int index = 0;
            boolean valid = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                int slot = recipe instanceof ShapedRecipe shaped ? index % shaped.getWidth() + index / shaped.getWidth() * 3 : index;
                index++;
                if (ingredient.isEmpty()) continue;
                LinkedHashSet<ItemKey> candidates = new LinkedHashSet<>();
                for (ItemStack display : ingredient.getItems()) stockByItem.getOrDefault(display.getItem(), List.of()).stream().filter(key -> ingredient.test(key.sample())).forEach(candidates::add);
                for (ItemStack stack : ingredient.getItems()) if (!stack.isEmpty()) candidates.add(new ItemKey(stack));
                if (candidates.isEmpty() || slot > 8) { valid = false; break; }
                ingredients.add(new CraftRecipe.Ingredient<>(List.copyOf(candidates), 1, ingredientTag(ingredient)));
                slots.add(slot);
            }
            if (!valid || ingredients.isEmpty()) continue;
            int ticks = recipe instanceof AbstractCookingRecipe cooking ? cooking.getCookingTime() : process.equals("crafting") ? 36 : 20;
            String id = holder.id().toString();
            result.add(new CraftRecipe<>(id, new ItemKey(output), output.getCount(), process, ingredients, 0, ticks));
            bindings.put(id, new Binding(holder, List.copyOf(slots)));
            } catch (RuntimeException failure) { CraftingService.LOGGER.warn("Skipping invalid processing recipe {}", holder.id(), failure); }
        }
        return result;
    }
    static String ingredientTag(Ingredient ingredient) {
        if(!ingredient.isSimple())return "";
        net.minecraft.world.item.crafting.Ingredient.Value[] values;
        try{values=ingredient.getValues();}catch(IllegalStateException custom){return "";}
        return values.length==1 && values[0] instanceof Ingredient.TagValue tag ? tag.tag().location().toString() : "";
    }
    private static String process(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe && recipe.canCraftInDimensions(3, 3)) return "crafting";
        if (recipe.getType() == RecipeType.SMELTING) return "smelting";
        if (recipe.getType() == RecipeType.BLASTING) return "blasting";
        if (recipe.getType() == RecipeType.SMOKING) return "smoking";
        if (recipe.getType() == RecipeType.STONECUTTING) return "stonecutting";
        return null;
    }
    static ServerLevel world(CraftingService.NetworkAccess access, GlobalPos position) {
        return access.level().getServer().getLevel(position.dimension());
    }
    @Override public boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node) {
        ServerLevel level = world(access, position);
        if (level == null || !level.hasChunkAt(position.pos()) || !bindings.containsKey(node.recipe().id())) return false;
        var state = level.getBlockState(position.pos());
        return switch (node.recipe().process()) {
            case "crafting" -> VisualWorkbenchCompatibility.isCraftingTable(level,position.pos());
            case "smelting" -> state.is(Blocks.FURNACE);
            case "blasting" -> state.is(Blocks.BLAST_FURNACE);
            case "smoking" -> state.is(Blocks.SMOKER);
            case "stonecutting" -> state.is(Blocks.STONECUTTER);
            default -> false;
        };
    }
    @Override public void previewDelivery(CraftingService.NetworkAccess access,GlobalPos position,CraftPlan.Node<ItemKey> node,Map<ItemKey,Long> arrived,int remainingTicks){
        if(!node.recipe().process().equals("crafting")&&!node.recipe().process().equals("stonecutting"))return;
        var binding=bindings.get(node.recipe().id());if(binding==null)return;
        var available=new HashMap<>(arrived);List<ItemStack> grid=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));
        for(int i=0;i<node.selected().size();i++){var input=node.selected().get(i);long count=available.getOrDefault(input.key(),0L);if(count>=input.count()){grid.set(binding.slots().get(i),input.key().sample());available.put(input.key(),count-input.count());}}
        access.stagingVisual(position,grid,remainingTicks);
    }
    @Override public CraftScheduler.Operation<ItemKey> start(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node) {
        if (!supports(access, position, node)) return null;
        Binding binding = bindings.get(node.recipe().id());
        ServerLevel level = world(access, position);
        if (node.recipe().process().equals("crafting") || node.recipe().process().equals("stonecutting")) {
            // Craft from scheduler-owned inputs. A Visual Workbench keeps its independent
            // player grid and preview output; neither is read or mutated by this operation.
            List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
            for (int i = 0; i < node.selected().size(); i++) grid.set(binding.slots().get(i), node.selected().get(i).key().sample());
            RecipeInput input = binding.holder().value() instanceof CraftingRecipe ? CraftingInput.of(3, 3, grid) : new SingleRecipeInput(grid.getFirst());
            ItemStack result = assemble(binding.holder().value(), input, level);
            if (result.isEmpty() || !new ItemKey(result).equals(node.recipe().output()) || result.getCount() != node.recipe().outputCount())
                throw new IllegalStateException("Recipe output changed: " + node.recipe().id());
            Map<ItemKey, Long> produced = counts(List.of(result));
            for (ItemStack remainder : remaining(binding.holder().value(), input)) add(produced, remainder);
            CraftingService.animateTable(access, position, grid, result.copy(), node.recipe().durationTicks());
            return new CraftScheduler.Operation<>() {
                int ticks;
                boolean settled;
                @Override public Map<ItemKey, Long> poll() {
                    ServerLevel current = world(access, position);
                    if (current == null || !current.hasChunkAt(position.pos())) return null;
                    if (!supports(access, position, node)) throw new IllegalStateException("Workstation removed: " + position.pos());
                    if (!com.cappleapple.astralrepository.AstralConfig.instantAutomaticLogistics.get() && ++ticks < node.recipe().durationTicks()) return null;
                    settled = true;
                    return Map.copyOf(produced);
                }
                @Override public Map<ItemKey, Long> cancel() { if (settled) return Map.of(); settled = true; return node.inputs(); }
                @Override public Map<ItemKey, Long> recoverable() { return settled ? Map.of() : node.inputs(); }
            };
        }
        if (!(level.getBlockEntity(position.pos()) instanceof AbstractFurnaceBlockEntity furnace)) return null;
        if (!furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty()) return null;
        ItemStack ingredient = node.selected().getFirst().key().sample();
        RecipeType<? extends AbstractCookingRecipe> type = cookingType(node.recipe().process());
        var actual = level.getRecipeManager().getRecipeFor(type, new SingleRecipeInput(ingredient), level);
        if (actual.isEmpty()) return null;
        ItemStack expected = actual.get().value().assemble(new SingleRecipeInput(ingredient), level.registryAccess());
        if (expected.isEmpty() || !new ItemKey(expected).equals(node.recipe().output()) || expected.getCount() != node.recipe().outputCount()) return null;
        boolean lit = level.getBlockState(position.pos()).getValue(AbstractFurnaceBlock.LIT);
        FuelDelivery fuel = FuelDelivery.EMPTY;
        if (!lit && furnace.getItem(1).isEmpty()) {
            fuel = takeFuel(access, type, position);
        }
        // Inputs have arrived. Fuel remains operation-owned until its separate flight completes.
        furnace.setItem(0, ingredient.copy());
        furnace.setChanged();
        return new FurnaceOperation(access, position, node, furnace, ingredient, expected, fuel, type);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack assemble(Recipe recipe, RecipeInput input, ServerLevel level) {
        if (!recipe.matches(input, level)) throw new IllegalStateException("Recipe ingredients no longer match");
        return recipe.assemble(input, level.registryAccess());
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<ItemStack> remaining(Recipe recipe, RecipeInput input) { return recipe.getRemainingItems(input); }
    private static RecipeType<? extends AbstractCookingRecipe> cookingType(String process) {
        return switch (process) { case "blasting" -> RecipeType.BLASTING; case "smoking" -> RecipeType.SMOKING; default -> RecipeType.SMELTING; };
    }
    private record FuelDelivery(ItemStack stack,int ticks){static final FuelDelivery EMPTY=new FuelDelivery(ItemStack.EMPTY,0);}
    private static FuelDelivery takeFuel(CraftingService.NetworkAccess access, RecipeType<?> type, GlobalPos position) {
        for (var entry : access.snapshot().entrySet().stream().sorted(Comparator.<Map.Entry<ItemKey, Long>>comparingInt(e -> e.getKey().sample().getBurnTime(type)).reversed()).toList()) {
            if (entry.getValue() <= 0 || entry.getKey().sample().getBurnTime(type) <= 0) continue;
            int[] delay={0};
            ItemStack extracted = access.extractForDelivery(entry.getKey(),1,position,(from,stack)->{
                delay[0]=Math.max(delay[0],access.travelTicks(from,position));CraftingService.animate(access,from,position,stack,Math.max(1,delay[0]));
            });
            if (!extracted.isEmpty()) return new FuelDelivery(extracted,delay[0]);
        }
        return FuelDelivery.EMPTY;
    }
    static Map<ItemKey, Long> counts(Collection<ItemStack> items) {
        Map<ItemKey, Long> result = new LinkedHashMap<>();
        items.forEach(stack -> add(result, stack));
        return result;
    }
    static void add(Map<ItemKey, Long> result, ItemStack stack) {
        if (!stack.isEmpty()) result.merge(new ItemKey(stack), (long) stack.getCount(), Math::addExact);
    }
    private static final class FurnaceOperation implements CraftScheduler.Operation<ItemKey> {
        final CraftingService.NetworkAccess access;
        final GlobalPos position;
        final CraftPlan.Node<ItemKey> node;
        final AbstractFurnaceBlockEntity original;
        final ItemStack input;
        final ItemStack expected;
        final RecipeType<?> type;
        ItemStack ownedFuel=ItemStack.EMPTY,pendingFuel;
        int ticks,fuelTicks;
        boolean settled;
        FurnaceOperation(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node,
                         AbstractFurnaceBlockEntity furnace, ItemStack input, ItemStack expected, FuelDelivery fuel, RecipeType<?> type) {
            this.access = access; this.position = position; this.node = node; this.original = furnace;
            this.input = input.copy(); this.expected = expected.copy(); this.pendingFuel = fuel.stack().copy(); this.fuelTicks=fuel.ticks(); this.type = type;
            if(fuelTicks==0)acceptFuel();
        }
        @Override public Map<ItemKey, Long> poll() {
            ServerLevel level = world(access, position);
            if (level == null || !level.hasChunkAt(position.pos())) return null;
            if (level.getBlockEntity(position.pos()) != original) throw new IllegalStateException("Processor removed; its physical contents remain in-world");
            if(fuelTicks>0)fuelTicks--;if(fuelTicks==0)acceptFuel();
            ItemStack output = original.getItem(2);
            if (ItemStack.isSameItemSameComponents(output, expected) && output.getCount() >= expected.getCount() && original.getItem(0).isEmpty()) {
                Map<ItemKey, Long> results = counts(List.of(original.removeItem(2, expected.getCount())));
                reclaimFuel(results);returnPendingFuel(results);
                original.setChanged();
                settled = true;
                return results;
            }
            if (++ticks > Math.max(1200, node.recipe().durationTicks() * 8)) throw new IllegalStateException("Processor stalled; check fuel and external extraction at " + position.pos());
            if (pendingFuel.isEmpty() && original.getItem(1).isEmpty() && !original.getItem(0).isEmpty() && !level.getBlockState(position.pos()).getValue(AbstractFurnaceBlock.LIT)) {
                FuelDelivery fuel = takeFuel(access, type, position);
                pendingFuel=fuel.stack().copy();fuelTicks=fuel.ticks();if(fuelTicks==0)acceptFuel();
            }
            return null;
        }
        @Override public Map<ItemKey, Long> cancel() {
            if (settled) return Map.of();
            settled = true;
            ServerLevel level = world(access, position);
            // Unloaded or broken processors retain/drop their physical contents; never synthesize a refund.
            Map<ItemKey, Long> result = new LinkedHashMap<>();returnPendingFuel(result);
            if (level == null || !level.hasChunkAt(position.pos()) || level.getBlockEntity(position.pos()) != original) return result;
            if (ItemStack.isSameItemSameComponents(original.getItem(0), input)) add(result, original.removeItem(0, input.getCount()));
            if (ItemStack.isSameItemSameComponents(original.getItem(2), expected)) add(result, original.removeItem(2, expected.getCount()));
            reclaimFuel(result);
            original.setChanged();
            return result;
        }
        @Override public Map<ItemKey,Long> recoverable(){return settled||pendingFuel.isEmpty()?Map.of():counts(List.of(pendingFuel));}
        private void returnPendingFuel(Map<ItemKey,Long> result){add(result,pendingFuel);pendingFuel=ItemStack.EMPTY;}
        private void acceptFuel(){
            if(pendingFuel.isEmpty()||!original.getItem(1).isEmpty())return;
            original.setItem(1,pendingFuel);ownedFuel=pendingFuel.copy();pendingFuel=ItemStack.EMPTY;original.setChanged();
        }
        private void reclaimFuel(Map<ItemKey, Long> result) {
            if (ownedFuel.isEmpty()) return;
            ItemStack current = original.getItem(1);
            if (ItemStack.isSameItemSameComponents(current, ownedFuel)) add(result, original.removeItem(1, ownedFuel.getCount()));
            else if (ownedFuel.hasCraftingRemainingItem() && ItemStack.isSameItemSameComponents(current, ownedFuel.getCraftingRemainingItem()))
                add(result, original.removeItem(1, ownedFuel.getCraftingRemainingItem().getCount()));
            ownedFuel = ItemStack.EMPTY;
        }
    }
}
