package com.cappleapple.astralrepository.port;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
public final class FuelCompat {
 public static int burnTicks(ItemStack stack,ServerLevel level,BlockPos pos,RecipeType<?> type){
  var entity=level.getBlockEntity(pos);if(!(entity instanceof Container container)||stack.isEmpty())return 0;
  var params=new LootParams.Builder(level).withParameter(LootContextParams.BLOCK_STATE,entity.getBlockState())
   .withParameter(LootContextParams.BLOCK_ENTITY,entity).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos))
   .withParameter(LootContextParams.CONTAINER,container)
   .create(LootContextParamSets.CONTAINER_PROCESS);
  return ResolvableInt.getFromItem(stack,DataComponents.COOKING_FUEL,CookingFuel::burnTime,new LootContext.Builder(params).create(Optional.empty()),0);
 }
 private FuelCompat(){}
}