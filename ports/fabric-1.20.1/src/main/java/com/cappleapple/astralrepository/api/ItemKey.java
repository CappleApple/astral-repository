package com.cappleapple.astralrepository.api;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Immutable item identity, including data components but excluding count. */
public final class ItemKey implements ResourceKey {
    private final ItemStack sample;
    private final int hash;
    public ItemKey(ItemStack stack) {
        if (Objects.requireNonNull(stack).isEmpty()) throw new IllegalArgumentException("Empty item identity");
        sample = stack.copyWithCount(1);
        hash = com.cappleapple.astralrepository.platform.Backport.itemHash(sample);
    }
    public ItemStack sample() { return sample.copy(); }
    /** Compare live contents without allocating another immutable identity. */
    public boolean matches(ItemStack stack) { return !stack.isEmpty() && ItemStack.isSameItemSameTags(sample, stack); }
    public ResourceLocation id() { return BuiltInRegistries.ITEM.getKey(sample.getItem()); }
    public ResourceLocation type() { return ResourceKinds.ITEM; }
    @Override public boolean equals(Object other) {
        return other instanceof ItemKey key && ItemStack.isSameItemSameTags(sample, key.sample);
    }
    @Override public int hashCode() { return hash; }
    @Override public String toString() { return id().toString(); }
}