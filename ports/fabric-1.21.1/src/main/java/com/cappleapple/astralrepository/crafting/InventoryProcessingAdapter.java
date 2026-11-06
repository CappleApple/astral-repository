package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.CompatConfig;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.cappleapple.astralrepository.platform.capabilities.Capabilities;
import com.cappleapple.astralrepository.platform.items.IItemHandler;
import java.util.*;

/** Delivers real recipe inputs and collects only physically produced machine outputs. */
public final class InventoryProcessingAdapter implements ProcessingAdapter {
    private record Binding(ProcessingRules.Rule rule, Map<ItemKey, Long> possibleOutputs) {}
    private final Map<String, Binding> bindings = new HashMap<>();
    private final Set<GlobalPos> unavailable;
    public InventoryProcessingAdapter() { unavailable = new HashSet<>(); }
    private InventoryProcessingAdapter(InventoryProcessingAdapter source) { unavailable = source.unavailable; bindings.putAll(source.bindings); }
    @Override public ProcessingAdapter capturePlan() { return new InventoryProcessingAdapter(this); }
    @Override public List<CraftRecipe<ItemKey>> recipes(CraftingService.NetworkAccess access) {
        bindings.clear();
        List<CraftRecipe<ItemKey>> result = new ArrayList<>();
        var snapshot = access.snapshot();
        Map<net.minecraft.world.item.Item, List<ItemKey>> stockByItem = snapshot.keySet().stream().collect(java.util.stream.Collectors.groupingBy(key -> key.sample().getItem()));
        for (var rule : ProcessingRules.rules()) {
            if (rule.block().getNamespace().equals("create") && !CompatConfig.create.get()) continue;
            if (!BuiltInRegistries.BLOCK.containsKey(rule.block())) continue;
            for (var holder : access.level().getRecipeManager().getRecipes()) {
                Recipe<?> recipe = holder.value();
                if (!rule.recipeType().equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())) || !MachineRecipeSupport.itemOnly(recipe)) continue;
                var inputs = MachineRecipeSupport.inputs(recipe);
                if (inputs.isEmpty() || inputs.size() > 9) continue;
                if (rule.block().getNamespace().equals("create") && inputs.size() != 1) continue;
                ItemStack output = MachineRecipeSupport.output(recipe, access.level().registryAccess());
                if (output.isEmpty()) continue;
                Map<ItemKey, Long> outputs = possibleOutputs(recipe, output);
                if (outputs.isEmpty()) continue;
                List<CraftRecipe.Ingredient<ItemKey>> ingredients = new ArrayList<>();
                for (var input : inputs) {
                    Ingredient ingredient = input.ingredient();
                    Set<ItemKey> alternatives = new LinkedHashSet<>();
                    for (ItemStack display : ingredient.getItems()) stockByItem.getOrDefault(display.getItem(), List.of()).stream().filter(key -> ingredient.test(key.sample())).forEach(alternatives::add);
                    for (ItemStack stack : ingredient.getItems()) if (!stack.isEmpty()) alternatives.add(new ItemKey(stack));
                    alternatives.remove(new ItemKey(output));
                    if (alternatives.isEmpty() || input.count() < 1 || input.count() > 64) break;
                    ingredients.add(new CraftRecipe.Ingredient<>(List.copyOf(alternatives), input.count(), VanillaProcessingAdapter.ingredientTag(ingredient)));
                }
                if (ingredients.size() != inputs.size()) continue;
                String id = rule.id() + "/" + holder.id();
                String process = "inventory/" + rule.id();
                result.add(new CraftRecipe<>(id, new ItemKey(output), output.getCount(), process,
                        ingredients, rule.priority(), 100));
                bindings.put(id, new Binding(rule, outputs));
            }
        }
        return result;
    }
    private static Map<ItemKey, Long> possibleOutputs(Recipe<?> recipe, ItemStack main) {
        Map<ItemKey, Long> outputs = new LinkedHashMap<>();
        VanillaProcessingAdapter.add(outputs, main);
        if (recipe.getClass().getName().equals("rearth.oritech.init.recipes.OritechRecipe")) {
            try {
                outputs.clear();
                for (Object result : (List<?>) recipe.getClass().getMethod("getResults").invoke(recipe)) VanillaProcessingAdapter.add(outputs, (ItemStack) result);
                return Map.copyOf(outputs);
            } catch (ReflectiveOperationException | RuntimeException failure) { return Map.of(); }
        }
        if (!recipe.getClass().getName().startsWith("com.simibubi.create.")) return Map.copyOf(outputs);
        try {
            if (!((Collection<?>)recipe.getClass().getMethod("getFluidIngredients").invoke(recipe)).isEmpty()) return Map.of();
            List<?> results = (List<?>)recipe.getClass().getMethod("getRollableResults").invoke(recipe);
            if (results.isEmpty()) return Map.of();
            if (((Number)results.getFirst().getClass().getMethod("getChance").invoke(results.getFirst())).doubleValue() < 1) return Map.of();
            outputs.clear();
            for (Object result : results) VanillaProcessingAdapter.add(outputs, (ItemStack)result.getClass().getMethod("getStack").invoke(result));
            return Map.copyOf(outputs);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            CraftingService.LOGGER.warn("Skipping unsupported Create recipe API {}", recipe.getClass().getName());
            return Map.of();
        }
    }
    @Override public boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node) {
        if (unavailable.contains(position)) return false;
        Binding binding = bindings.get(node.recipe().id());
        if (binding == null) return false;
        ServerLevel level = VanillaProcessingAdapter.world(access, position);
        if (level == null || !level.hasChunkAt(position.pos())) return false;
        var rule = binding.rule();
        return level.getBlockState(position.pos()).is(BuiltInRegistries.BLOCK.get(rule.block()))
                && (rule.aboveBlock() == null || level.getBlockState(position.pos().above(rule.aboveOffset())).is(BuiltInRegistries.BLOCK.get(rule.aboveBlock())));
    }
    @Override public CraftScheduler.Operation<ItemKey> start(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node) {
        if (!supports(access, position, node) || node.selected().isEmpty()) return null;
        var rule = bindings.get(node.recipe().id()).rule();
        ServerLevel level = VanillaProcessingAdapter.world(access, position);
        var original = level.getBlockEntity(position.pos());
        IItemHandler input = com.cappleapple.astralrepository.platform.capabilities.Capabilities.find(level,Capabilities.ItemHandler.BLOCK, position.pos(), rule.inputSide());
        IItemHandler output = com.cappleapple.astralrepository.platform.capabilities.Capabilities.find(level,Capabilities.ItemHandler.BLOCK, position.pos(), rule.outputSide());
        var boundary = MachineInventory.find(original, rule, input, output);
        if (boundary == null) return null;
        return startBoundary(access, position, node, rule, bindings.get(node.recipe().id()).possibleOutputs(), boundary);
    }
    private CraftScheduler.Operation<ItemKey> startBoundary(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node,
                                                            ProcessingRules.Rule rule, Map<ItemKey, Long> possible, MachineInventory.Boundary boundary) {
        ServerLevel level = VanillaProcessingAdapter.world(access, position);
        var original = level.getBlockEntity(position.pos());
        var in = boundary.input(); var out = boundary.output();
        List<Integer> inputSlots = slots(rule.inputSlots(), in), outputSlots = slots(rule.outputSlots(), out);
        if (outputSlots.stream().anyMatch(slot -> !out.get(slot).isEmpty()) || inputSlots.stream().anyMatch(slot -> !in.get(slot).isEmpty())) return null;
        if (boundary.fuelSlot() >= 0 && !in.get(boundary.fuelSlot()).isEmpty() && !access.mayUseFuel(new ItemKey(in.get(boundary.fuelSlot())))) return null;
        List<ItemStack> ingredients = node.selected().stream().map(selected -> selected.key().sample().copyWithCount(Math.toIntExact(selected.count()))).toList();
        List<Integer> assigned = assign(in, inputSlots, ingredients);
        if (assigned == null) return null;
        return new CraftScheduler.Operation<>() {
            int elapsed;
            boolean settled;
            final Map<ItemKey, Long> held = new LinkedHashMap<>();
            final Map<Integer, ItemStack> inserted = new LinkedHashMap<>();
            ItemStack pendingFuel = ItemStack.EMPTY, ownedFuel = ItemStack.EMPTY;
            int fuelTicks;
            RuntimeException insertionFailure;
            {
                // Once any input is committed, return an operation even on partial rejection.
                // Its cancellation returns known leftovers and only physically recoverable inputs.
                for (int i = 0; i < ingredients.size(); i++) {
                    ItemStack ingredient = ingredients.get(i);
                    if (insertionFailure != null) { VanillaProcessingAdapter.add(held, ingredient); continue; }
                    try {
                        ItemStack remainder = in.insert(assigned.get(i), ingredient.copy(), false);
                        int committed = ingredient.getCount() - remainder.getCount();
                        if (committed > 0) inserted.put(assigned.get(i), ingredient.copyWithCount(committed));
                        VanillaProcessingAdapter.add(held, remainder);
                        if (!remainder.isEmpty()) insertionFailure = new IllegalStateException("Machine rejected an input after simulation");
                    } catch (RuntimeException failure) {
                        insertionFailure = failure; unavailable.add(position);
                        CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(new ItemKey(ingredient), (long) ingredient.getCount()));
                        CraftingService.LOGGER.error("Machine insertion outcome uncertain at {}; the offered input is not refunded", position, failure);
                    }
                }
            }
            private boolean available() {
                ServerLevel current = VanillaProcessingAdapter.world(access, position);
                return current != null && current.hasChunkAt(position.pos());
            }
            private boolean sameMachine() { return level.getBlockEntity(position.pos()) == original; }
            public Map<ItemKey, Long> poll() {
                if (!available()) return null;
                if (!sameMachine()) throw new IllegalStateException("Processing machine removed; contents remain in-world");
                if (insertionFailure != null) throw insertionFailure;
                fuel();
                long ready = 0;
                for (int slot : outputSlots) {
                    ItemStack stack = out.get(slot);
                    if (!stack.isEmpty() && new ItemKey(stack).equals(node.recipe().output())) ready += stack.getCount();
                }
                if (ready >= node.recipe().outputCount()) {
                    collectOutputs();
                    reclaimFuel();
                    settled = true;
                    return Map.copyOf(held);
                }
                if (++elapsed > rule.timeoutTicks()) throw new IllegalStateException("Processing machine stalled; check power and setup at " + position.pos());
                return null;
            }
            private void collectOutputs() {
                for (int slot : outputSlots) {
                    ItemStack stack = out.get(slot);
                    if (stack.isEmpty()) continue;
                    ItemKey key = new ItemKey(stack);
                    long allowed = possible.getOrDefault(key, 0L) - held.getOrDefault(key, 0L);
                    if (allowed > 0) VanillaProcessingAdapter.add(held, out.extract(slot, (int)Math.min(allowed, stack.getCount())));
                }
            }
            public Map<ItemKey, Long> cancel() {
                if (settled) return Map.of();
                settled = true;
                VanillaProcessingAdapter.add(held, pendingFuel); pendingFuel = ItemStack.EMPTY;
                if (available() && sameMachine()) {
                    try {
                        for (var entry : inserted.entrySet()) {
                            ItemStack remaining = in.get(entry.getKey());
                            if (ItemStack.isSameItemSameComponents(remaining, entry.getValue())) VanillaProcessingAdapter.add(held, in.extract(entry.getKey(), entry.getValue().getCount()));
                        }
                        collectOutputs();
                        reclaimFuel();
                    } catch (RuntimeException failure) { unavailable.add(position); CraftingService.LOGGER.error("Machine cleanup incomplete; remaining physical items retained at {}", position, failure); }
                }
                return Map.copyOf(held);
            }
            public Map<ItemKey, Long> recoverable() { var result = new LinkedHashMap<>(held); VanillaProcessingAdapter.add(result, pendingFuel); return settled ? Map.of() : Map.copyOf(result); }
            private void fuel() {
                int slot = boundary.fuelSlot();
                if (slot < 0) return;
                if (fuelTicks > 0) fuelTicks--;
                if (!pendingFuel.isEmpty()) {
                    if (fuelTicks > 0 || !in.get(slot).isEmpty()) return;
                    ItemStack offered = pendingFuel; pendingFuel = ItemStack.EMPTY;
                    try {
                        ItemStack remainder = in.insert(slot, offered, false);
                        ownedFuel = offered.copyWithCount(offered.getCount() - remainder.getCount());
                        VanillaProcessingAdapter.add(held, remainder);
                    } catch (RuntimeException failure) {
                        unavailable.add(position);
                        CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), Map.of(new ItemKey(offered), (long) offered.getCount()));
                        throw failure;
                    }
                    return;
                }
                if (!in.get(slot).isEmpty()) return;
                for (var entry : access.snapshot().entrySet()) {
                    ItemStack sample = entry.getKey().sample();
                    if (entry.getValue() <= 0 || !access.mayUseFuel(entry.getKey()) || net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().getOrDefault(sample.getItem(), 0) <= 0 || !in.insert(slot, sample, true).isEmpty()) continue;
                    pendingFuel = access.extractForDelivery(entry.getKey(), 1, position, (from, stack) -> {
                        fuelTicks = Math.max(fuelTicks, access.travelTicks(from, position)); CraftingService.animate(access, from, position, stack, Math.max(1, fuelTicks));
                    });
                    break;
                }
            }
            private void reclaimFuel() {
                VanillaProcessingAdapter.add(held, pendingFuel); pendingFuel = ItemStack.EMPTY;
                if (!ownedFuel.isEmpty()) {
                    ItemStack current = in.get(boundary.fuelSlot());
                    if (ItemStack.isSameItemSameComponents(current, ownedFuel)) VanillaProcessingAdapter.add(held, in.extract(boundary.fuelSlot(), ownedFuel.getCount()));
                    else if (ownedFuel.getItem().hasCraftingRemainingItem() && ItemStack.isSameItemSameComponents(current, new ItemStack(ownedFuel.getItem().getCraftingRemainingItem())))
                        VanillaProcessingAdapter.add(held, in.extract(boundary.fuelSlot(), new ItemStack(ownedFuel.getItem().getCraftingRemainingItem()).getCount()));
                    ownedFuel = ItemStack.EMPTY;
                }
            }
        };
    }
    private static List<Integer> slots(List<Integer> configured, MachineInventory.Slots handler) {
        if (configured.isEmpty()) return java.util.stream.IntStream.range(0, handler.size()).boxed().toList();
        return configured.stream().filter(slot -> slot < handler.size()).toList();
    }
    private static List<Integer> assign(MachineInventory.Slots input, List<Integer> slots, List<ItemStack> ingredients) {
        return MachineSlotAssignment.assign(slots, ingredients, (slot, stack) -> input.insert(slot, stack, true).isEmpty());
    }
}
