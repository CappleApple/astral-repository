package com.cappleapple.astralrepository.platform.capabilities;
import net.minecraft.core.*;import net.minecraft.world.level.Level;import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;import com.cappleapple.astralrepository.platform.items.IItemHandler;import com.cappleapple.astralrepository.platform.fluids.capability.IFluidHandler;import com.cappleapple.astralrepository.platform.energy.IEnergyStorage;
public final class Capabilities {
 public static final class ItemHandler {public static final BlockCapability<IItemHandler,Direction> BLOCK=new BlockCapability<>((l,p,s)->{var storage=ItemStorage.SIDED.find(l,p,s);return storage==null?null:new FabricTransfer.ItemHandler(storage);});}
 public static final class FluidHandler {public static final BlockCapability<IFluidHandler,Direction> BLOCK=new BlockCapability<>((l,p,s)->{var storage=FluidStorage.SIDED.find(l,p,s);return storage==null?null:new FabricTransfer.FluidHandler(storage);});}
 public static final class EnergyStorage {public static final BlockCapability<IEnergyStorage,Direction> BLOCK=new BlockCapability<>((l,p,s)->{var storage=team.reborn.energy.api.EnergyStorage.SIDED.find(l,p,s);return storage==null?null:new FabricTransfer.EnergyHandler(storage);});}
 public static <T,C> T find(Level level,BlockCapability<T,C> capability,BlockPos pos,C context){return capability==null?null:capability.find(level,pos,context);}
}
