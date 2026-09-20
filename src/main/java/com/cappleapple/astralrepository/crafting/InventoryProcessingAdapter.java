package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.compat.CompatConfig;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.*;

/** Executes deterministic single-input recipes through real sided machine capabilities. */
public final class InventoryProcessingAdapter implements ProcessingAdapter {
    private record Binding(ProcessingRules.Rule rule, Map<ItemKey, Long> possibleOutputs) {}
    private final Map<String, Binding> bindings = new HashMap<>();
    private final Set<GlobalPos> unavailable = new HashSet<>();
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
                if (!rule.recipeType().equals(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())) || recipe.getIngredients().size() != 1) continue;
                ItemStack output = recipe.getResultItem(access.level().registryAccess());
                if (output.isEmpty()) continue;
                Map<ItemKey, Long> outputs = possibleOutputs(recipe, output);
                if (outputs.isEmpty()) continue;
                Ingredient ingredient = recipe.getIngredients().getFirst();
                Set<ItemKey> alternatives = new LinkedHashSet<>();
                for (ItemStack display : ingredient.getItems()) stockByItem.getOrDefault(display.getItem(), List.of()).stream().filter(key -> ingredient.test(key.sample())).forEach(alternatives::add);
                for (ItemStack stack : ingredient.getItems()) if (!stack.isEmpty()) alternatives.add(new ItemKey(stack));
                alternatives.remove(new ItemKey(output));
                if (alternatives.isEmpty()) continue;
                String id = rule.id() + "/" + holder.id();
                String process = "inventory/" + rule.id();
                result.add(new CraftRecipe<>(id, new ItemKey(output), output.getCount(), process,
                        List.of(new CraftRecipe.Ingredient<>(List.copyOf(alternatives), 1, VanillaProcessingAdapter.ingredientTag(ingredient))), rule.priority(), 100));
                bindings.put(id, new Binding(rule, outputs));
            }
        }
        return result;
    }
    private static Map<ItemKey, Long> possibleOutputs(Recipe<?> recipe, ItemStack main) {
        Map<ItemKey, Long> outputs = new LinkedHashMap<>();
        VanillaProcessingAdapter.add(outputs, main);
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
        if (!supports(access, position, node) || node.selected().size() != 1) return null;
        var rule = bindings.get(node.recipe().id()).rule();
        ServerLevel level = VanillaProcessingAdapter.world(access, position);
        var original = level.getBlockEntity(position.pos());
        IItemHandler input = level.getCapability(Capabilities.ItemHandler.BLOCK, position.pos(), rule.inputSide());
        IItemHandler output = level.getCapability(Capabilities.ItemHandler.BLOCK, position.pos(), rule.outputSide());
        if (input == null || output == null) return null;
        List<Integer> inputSlots = slots(rule.inputSlots(), input), outputSlots = slots(rule.outputSlots(), output);
        if (outputSlots.stream().anyMatch(slot -> !output.getStackInSlot(slot).isEmpty())) return null;
        ItemStack ingredient = node.selected().getFirst().key().sample();
        int insertionSlot = -1;
        for (int slot : inputSlots) {
            if (!input.getStackInSlot(slot).isEmpty()) return null;
            if (insertionSlot < 0 && input.insertItem(slot, ingredient, true).isEmpty()) insertionSlot = slot;
        }
        if (insertionSlot < 0) return null;
        int assignedSlot = insertionSlot;
        try {
            ItemStack remainder = input.insertItem(assignedSlot, ingredient.copy(), false);
            if (!remainder.isEmpty()) return null;
        } catch (RuntimeException failure) {
            unavailable.add(position);
            CraftRecoveryData.get(access.level().getServer()).uncertain(access.origin(), node.inputs());
            CraftingService.LOGGER.error("Machine insertion outcome uncertain at {}; input stays claimed without a synthetic refund", position, failure);
            return new CraftScheduler.Operation<>() {
                public Map<ItemKey, Long> poll() { throw new IllegalStateException("Machine input insertion outcome is uncertain"); }
                public Map<ItemKey, Long> cancel() { return Map.of(); }
            };
        }
        Map<ItemKey, Long> possible = bindings.get(node.recipe().id()).possibleOutputs();
        return new CraftScheduler.Operation<>() {
            int elapsed;
            boolean settled;
            final Map<ItemKey, Long> held = new LinkedHashMap<>();
            private boolean available() {
                ServerLevel current = VanillaProcessingAdapter.world(access, position);
                return current != null && current.hasChunkAt(position.pos());
            }
            private boolean sameMachine() { return level.getBlockEntity(position.pos()) == original; }
            public Map<ItemKey, Long> poll() {
                if (!available()) return null;
                if (!sameMachine()) throw new IllegalStateException("Processing machine removed; contents remain in-world");
                long ready = 0;
                for (int slot : outputSlots) {
                    ItemStack stack = output.getStackInSlot(slot);
                    if (!stack.isEmpty() && new ItemKey(stack).equals(node.recipe().output())) ready += stack.getCount();
                }
                if (ready >= node.recipe().outputCount()) {
                    collectOutputs();
                    settled = true;
                    return Map.copyOf(held);
                }
                if (++elapsed > rule.timeoutTicks()) throw new IllegalStateException("Processing machine stalled; check power and setup at " + position.pos());
                return null;
            }
            private void collectOutputs() {
                for (int slot : outputSlots) {
                    ItemStack stack = output.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    ItemKey key = new ItemKey(stack);
                    long allowed = possible.getOrDefault(key, 0L) - held.getOrDefault(key, 0L);
                    if (allowed > 0) VanillaProcessingAdapter.add(held, output.extractItem(slot, (int)Math.min(allowed, stack.getCount()), false));
                }
            }
            public Map<ItemKey, Long> cancel() {
                if (settled) return Map.of();
                settled = true;
                if (available() && sameMachine()) {
                    try {
                        ItemStack remaining = input.getStackInSlot(assignedSlot);
                        if (ItemStack.isSameItemSameComponents(remaining, ingredient)) VanillaProcessingAdapter.add(held, input.extractItem(assignedSlot, 1, false));
                        collectOutputs();
                    } catch (RuntimeException failure) { unavailable.add(position); CraftingService.LOGGER.error("Machine cleanup incomplete; remaining physical items retained at {}", position, failure); }
                }
                return Map.copyOf(held);
            }
            public Map<ItemKey, Long> recoverable() { return settled ? Map.of() : Map.copyOf(held); }
        };
    }
    private static List<Integer> slots(List<Integer> configured, IItemHandler handler) {
        if (configured.isEmpty()) return java.util.stream.IntStream.range(0, handler.getSlots()).boxed().toList();
        return configured.stream().filter(slot -> slot < handler.getSlots()).toList();
    }
}
