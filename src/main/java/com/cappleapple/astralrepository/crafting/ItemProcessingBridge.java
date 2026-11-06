package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.ResourceKey;
import java.util.*;
import net.minecraft.core.GlobalPos;

/** Retains the existing item-only extension contract inside the resource-aware coordinator. */
final class ItemProcessingBridge implements ResourceProcessingAdapter {
    private final ProcessingAdapter delegate;
    ItemProcessingBridge(ProcessingAdapter delegate) { this.delegate = delegate; }
    @Override public ResourceProcessingAdapter capturePlan() { return new ItemProcessingBridge(delegate.capturePlan()); }
    @Override public List<CraftRecipe<ResourceKey>> recipes(CraftingService.NetworkAccess access) {
        return delegate.recipes(access).stream().map(ItemProcessingBridge::resourceRecipe).toList();
    }
    @Override public boolean supports(CraftingService.NetworkAccess access, GlobalPos at, CraftPlan.Node<ResourceKey> node) {
        return delegate.supports(access, at, itemNode(node));
    }
    @Override public void previewDelivery(CraftingService.NetworkAccess access, GlobalPos at,
                                          CraftPlan.Node<ResourceKey> node, Map<ResourceKey, Long> arrived, int ticks) {
        delegate.previewDelivery(access, at, itemNode(node), items(arrived), ticks);
    }
    @Override public CraftScheduler.Operation<ResourceKey> start(CraftingService.NetworkAccess access, GlobalPos at, CraftPlan.Node<ResourceKey> node) {
        var operation = delegate.start(access, at, itemNode(node));
        if (operation == null) return null;
        return new CraftScheduler.Operation<>() {
            public Map<ResourceKey, Long> poll() { var result = operation.poll(); return result == null ? null : resources(result); }
            public Map<ResourceKey, Long> cancel() { return resources(operation.cancel()); }
            public Map<ResourceKey, Long> recoverable() { return resources(operation.recoverable()); }
            public boolean mayReturnShortOutput() { return operation.mayReturnShortOutput(); }
        };
    }
    static CraftRecipe<ResourceKey> resourceRecipe(CraftRecipe<ItemKey> recipe) {
        return new CraftRecipe<>(recipe.id(), recipe.output(), recipe.outputCount(), recipe.process(),
                recipe.ingredients().stream().map(input -> new CraftRecipe.Ingredient<ResourceKey>(
                        new ArrayList<>(input.alternatives()), input.count(), input.tag())).toList(), recipe.priority(), recipe.durationTicks(), recipe.returnedSeedCount());
    }
    static CraftPlan.Node<ItemKey> itemNode(CraftPlan.Node<ResourceKey> node) {
        var recipe = node.recipe();
        var items = new CraftRecipe<>(recipe.id(), item(recipe.output()), recipe.outputCount(), recipe.process(),
                recipe.ingredients().stream().map(input -> new CraftRecipe.Ingredient<>(
                        input.alternatives().stream().map(ItemProcessingBridge::item).toList(), input.count(), input.tag())).toList(), recipe.priority(), recipe.durationTicks(), recipe.returnedSeedCount());
        return new CraftPlan.Node<>(node.id(), items, node.selected().stream()
                .map(input -> new CraftPlan.Selection<>(item(input.key()), input.count())).toList(), node.dependencies());
    }
    private static ItemKey item(ResourceKey key) {
        if (key instanceof ItemKey item) return item;
        throw new IllegalArgumentException("Item processor received " + key.type());
    }
    private static Map<ItemKey, Long> items(Map<ResourceKey, Long> resources) {
        Map<ItemKey, Long> result = new LinkedHashMap<>();
        resources.forEach((key, count) -> result.put(item(key), count));
        return Map.copyOf(result);
    }
    private static Map<ResourceKey, Long> resources(Map<ItemKey, Long> items) { return Map.copyOf(items); }
}
