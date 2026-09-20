package com.cappleapple.astralrepository.compat;

import net.minecraft.world.item.ItemStack;

public final class CapacityCosts {
    private CapacityCosts() { }
    /** Whole capacity units rounded upward; exactCost preserves uncommon fractional stack-size costs. */
    public static long unitCost(ItemStack stack) { return StacksNotSlotsCapacity.unitCost(stack).ceil(1); }
    public static StacksNotSlotsCapacity.Fraction exactCost(ItemStack stack) { return StacksNotSlotsCapacity.unitCost(stack); }
}