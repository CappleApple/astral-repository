package com.cappleapple.astralrepository.smoke;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
final class GameTestHelper {
 private final ServerLevel level;private final BlockPos origin;
 GameTestHelper(ServerLevel level,int index){this.level=level;origin=new BlockPos(10000+index*128,100,512);}
 ServerLevel getLevel(){return level;}BlockPos absolutePos(BlockPos pos){return origin.offset(pos);}
 void setBlock(BlockPos pos,Block block){var absolute=absolutePos(pos);level.getChunkAt(absolute);for(var side:net.minecraft.core.Direction.values())com.cappleapple.astralrepository.content.RuneSurfaces.remove(level,absolute,side);level.removeBlock(absolute,false);level.setBlockAndUpdate(absolute,block.defaultBlockState());}
 net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return level.getBlockEntity(absolutePos(pos));}
 void assertTrue(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 void succeed(){}
}
