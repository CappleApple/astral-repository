package com.cappleapple.astralrepository.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
public final class Capabilities {
 public static final class ItemHandler {public static final BlockCapability<net.minecraftforge.items.IItemHandler,Direction> BLOCK=new BlockCapability<>(ForgeCapabilities.ITEM_HANDLER);}
 public static final class FluidHandler {public static final BlockCapability<net.minecraftforge.fluids.capability.IFluidHandler,Direction> BLOCK=new BlockCapability<>(ForgeCapabilities.FLUID_HANDLER);}
 public static final class EnergyStorage {public static final BlockCapability<net.minecraftforge.energy.IEnergyStorage,Direction> BLOCK=new BlockCapability<>(ForgeCapabilities.ENERGY);}
 public static <T,C> T get(Level level,BlockCapability<T,C> capability,BlockPos pos,C side){if(!level.hasChunkAt(pos))return null;var tile=level.getBlockEntity(pos);return tile==null?null:tile.getCapability(capability.capability(),side instanceof Direction d?d:null).orElse(null);}
}
