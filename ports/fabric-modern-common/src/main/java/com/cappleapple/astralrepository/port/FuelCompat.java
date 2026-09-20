package com.cappleapple.astralrepository.port;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
public final class FuelCompat {
 public static int burnTicks(ItemStack stack,ServerLevel level,BlockPos pos,RecipeType<?> type){return level.fuelValues().burnDuration(stack);}
 private FuelCompat(){}
}
