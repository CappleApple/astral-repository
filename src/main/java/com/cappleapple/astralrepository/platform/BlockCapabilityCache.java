package com.cappleapple.astralrepository.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.function.BooleanSupplier;
public final class BlockCapabilityCache<T,C> {
 private final net.minecraftforge.common.util.LazyOptional<T> value;
 private BlockCapabilityCache(BlockCapability<T,C> capability,ServerLevel level,BlockPos pos,C side,BooleanSupplier valid,Runnable invalidated){var tile=level.hasChunkAt(pos)?level.getBlockEntity(pos):null;value=tile==null?net.minecraftforge.common.util.LazyOptional.empty():tile.getCapability(capability.capability(),side instanceof Direction d?d:null);value.addListener(ignored->{if(valid.getAsBoolean())invalidated.run();});}
 public static <T,C> BlockCapabilityCache<T,C> create(BlockCapability<T,C> capability,ServerLevel level,BlockPos pos,C side,BooleanSupplier valid,Runnable invalidated){return new BlockCapabilityCache<>(capability,level,pos,side,valid,invalidated);}
 public T getCapability(){return value.orElse(null);}
}
