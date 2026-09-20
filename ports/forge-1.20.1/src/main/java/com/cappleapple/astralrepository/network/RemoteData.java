package com.cappleapple.astralrepository.network;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

/** The content item owns the component format; this bridge delegates its public reader. */
public final class RemoteData {
    public static GlobalPos bound(ItemStack stack) { return com.cappleapple.astralrepository.content.AstralNexusItem.bound(stack); }
    public static boolean attuned(ItemStack stack) { return com.cappleapple.astralrepository.content.AstralNexusItem.attuned(stack); }
    private RemoteData() {}
}
