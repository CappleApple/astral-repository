package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ResourceKey;
import java.util.List;
import java.util.Map;
import net.minecraft.core.GlobalPos;

/** Server-thread physical processing contract for item, fluid, chemical and scalar resources. */
public interface ResourceProcessingAdapter {
    /** Capture immutable recipe bindings for a plan before later catalog refreshes mutate discovery state. */
    default ResourceProcessingAdapter capturePlan() { return this; }
    default String workstationLabel(CraftingService.NetworkAccess access, String recipeId) { return ""; }
    /** Actual native recipes invoked by this profile; captured adapters must keep these identities immutable. */
    default java.util.Set<String> sourceRecipes(String recipeId) { return java.util.Set.of(recipeId); }
    /** Optional exact output metadata for native conversions; this never adds physical stock. */
    default void requestOutput(ResourceKey output) {}
    default void requestOutput(CraftingService.NetworkAccess access, ResourceKey output) { requestOutput(output); }
    List<CraftRecipe<ResourceKey>> recipes(CraftingService.NetworkAccess access);
    boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node);
    CraftScheduler.Operation<ResourceKey> start(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node);
    default void previewDelivery(CraftingService.NetworkAccess access, GlobalPos position,
                                 CraftPlan.Node<ResourceKey> node, Map<ResourceKey, Long> arrived, int remainingTicks) {}
    /** Detect an idle processor without depending on whether its recipes have been catalogued. */
    default boolean isProcessingPosition(CraftingService.NetworkAccess access, GlobalPos position) { return false; }
    /** Every casing or port of one physical processor must return the same identity. */
    default Object processorIdentity(CraftingService.NetworkAccess access, GlobalPos position) { return position; }
    /** Immutable machine configuration relevant to planning, including recipe modes and usage upgrades. */
    default Object catalogState(CraftingService.NetworkAccess access) { return List.of(); }
    /** Every physical output destination must be excluded from ordinary stock while owned. */
    default List<GlobalPos> claimedPositions(CraftingService.NetworkAccess access, GlobalPos position,
                                           CraftPlan.Node<ResourceKey> node) { return List.of(position); }
}
