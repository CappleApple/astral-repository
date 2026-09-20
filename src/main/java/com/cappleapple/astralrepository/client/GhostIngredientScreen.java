package com.cappleapple.astralrepository.client;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public interface GhostIngredientScreen {
    Rect2i ingredientArea();
    boolean acceptItem(ItemStack item);
    default boolean acceptFluid(FluidStack fluid){return false;}
}
