package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ResourceKey;
import java.util.List;
import java.util.Map;
import net.minecraft.core.GlobalPos;

/** Server-thread physical processing contract for item, fluid, chemical and scalar resources. */
public interface ResourceProcessingAdapter {
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
}
