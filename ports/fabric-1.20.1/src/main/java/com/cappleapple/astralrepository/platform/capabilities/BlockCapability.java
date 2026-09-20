package com.cappleapple.astralrepository.platform.capabilities;
import java.util.function.BiFunction;import java.util.IdentityHashMap;import java.util.Map;import net.minecraft.world.level.Level;import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.entity.BlockEntity;import net.minecraft.world.level.block.entity.BlockEntityType;
public final class BlockCapability<T,C> {
 public interface Lookup<T,C>{T find(Level level,BlockPos pos,C context);}
 private final Lookup<T,C> fallback;private final Map<BlockEntityType<?>,BiFunction<BlockEntity,C,T>> own=new IdentityHashMap<>();public BlockCapability(Lookup<T,C> fallback){this.fallback=fallback;}
 public <B extends BlockEntity> void register(BlockEntityType<B> type,BiFunction<B,C,T> factory){own.put(type,(tile,context)->factory.apply((B)tile,context));}
 public T find(Level level,BlockPos pos,C context){if(!level.hasChunkAt(pos))return null;var tile=level.getBlockEntity(pos);var f=tile==null?null:own.get(tile.getType());return f==null?fallback.find(level,pos,context):f.apply(tile,context);}
}
