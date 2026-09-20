package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.content.RuneSurfaces;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Synchronous production-server fixture; every assertion observes real providers and world state. */
final class PortGameTestHelper implements AutoCloseable {
    private final ServerLevel level;
    private final BlockPos origin;
    private final java.util.Set<BlockPos> placed=new HashSet<>();
    PortGameTestHelper(ServerLevel level,int index){this.level=level;origin=new BlockPos(128+index*128,100,128);}
    ServerLevel getLevel(){return level;}
    BlockPos absolutePos(BlockPos pos){return origin.offset(pos);}
    void setBlock(BlockPos pos,Block block){
        BlockPos world=absolutePos(pos);level.getChunkAt(world);placed.add(world);
        for(Direction side:Direction.values())RuneSurfaces.remove(level,world,side);
        level.setBlockAndUpdate(world,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(world,block.defaultBlockState());
    }
    net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return level.getBlockEntity(absolutePos(pos));}
    void assertTrue(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    void succeed(){}
    public void close(){for(var pos:placed){for(Direction side:Direction.values())RuneSurfaces.remove(level,pos,side);level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}}
}
