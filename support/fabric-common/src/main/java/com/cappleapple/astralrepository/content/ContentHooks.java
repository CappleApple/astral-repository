package com.cappleapple.astralrepository.content;

import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-only hooks wired by the network service during common initialization. */
public final class ContentHooks {
    public static BiConsumer<ServerLevel, BlockPos> topologyChanged = (level, pos) -> {};
    public static BiConsumer<ServerPlayer, GlobalPos> openNexus = (player, pos) -> {};
    public static BiConsumer<ServerPlayer, ItemStack> remoteUse = (player, stack) -> {};
    public static ToLongFunction<ItemStack> capacityCost = stack -> Math.max(1, (64L + stack.getMaxStackSize() - 1) / stack.getMaxStackSize());
    public static java.util.function.Function<ItemStack, com.cappleapple.astralrepository.compat.StacksNotSlotsCapacity.Fraction> capacityCostExact = stack -> new com.cappleapple.astralrepository.compat.StacksNotSlotsCapacity.Fraction(java.math.BigInteger.valueOf(64), java.math.BigInteger.valueOf(Math.max(1, stack.getMaxStackSize())));
    private ContentHooks() {}
}

