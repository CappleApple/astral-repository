package com.cappleapple.astralrepository.port;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
/** Maps the former sided results to the matching current swing source. */
public final class Interactions {
    public static InteractionResult sidedSuccess(boolean client) { return client ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER; }
    public static InteractionResult sidedSuccess(ItemStack stack, boolean client) { return client ? InteractionResult.SUCCESS.heldItemTransformedTo(stack) : InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(stack); }
    public static InteractionResult pass(ItemStack stack) { return InteractionResult.PASS; }
    public static InteractionResult fail(ItemStack stack) { return InteractionResult.FAIL; }
    private Interactions() {}
}
