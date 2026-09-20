package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import net.minecraft.core.GlobalPos;
import java.util.List;

/** Server-thread adapter for actual processing infrastructure. Implementations must isolate failures. */
public interface ProcessingAdapter {
    /** Recipe knowledge is separate from the final products exposed by bookshelves. */
    List<CraftRecipe<ItemKey>> recipes(CraftingService.NetworkAccess access);
    boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node);
    /** Show only delivered ingredients while the operation still owns all inputs in transit escrow. */
    default void previewDelivery(CraftingService.NetworkAccess access,GlobalPos position,CraftPlan.Node<ItemKey> node,java.util.Map<ItemKey,Long> arrived,int remainingTicks) {}
    /** Return null without mutation if unavailable. Accepted inputs become operation-owned. */
    CraftScheduler.Operation<ItemKey> start(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ItemKey> node);
}
