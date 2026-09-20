package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Synchronous native-provider fixtures use separate loaded regions and explicit worker clocks. */
public record TransitTestWorld(ServerLevel getLevel,BlockPos origin) {
    public BlockPos absolutePos(BlockPos pos){return origin.offset(pos);}
    public void setBlock(BlockPos pos,Block block){var p=absolutePos(pos);getLevel.getChunkAt(p);getLevel.removeBlock(p,false);getLevel.setBlockAndUpdate(p,block.defaultBlockState());}
    public BlockEntity getBlockEntity(BlockPos pos){return getLevel.getBlockEntity(absolutePos(pos));}
    public void assertTrue(boolean value,String what){if(!value)throw new AssertionError(what);}
    public void succeed(){}
    public static ResourceProvider scalar(String id,long[] amounts,int index){return new ResourceProvider(){
        public String id(){return id;}public Object identity(){return id;}public boolean valid(){return true;}public long capacity(){return 10000;}public net.minecraft.resources.Identifier resourceType(){return ResourceKinds.SOURCE;}
        public java.util.Map<ResourceKey,Long> snapshot(){return java.util.Map.of(ResourceKinds.ARS_SOURCE,amounts[index]);}
        public long insert(ResourceKey key,long n,boolean simulate){long accepted=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,10000-amounts[index]):0;if(!simulate)amounts[index]+=accepted;return accepted;}
        public long extract(ResourceKey key,long n,boolean simulate){long taken=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,amounts[index]):0;if(!simulate)amounts[index]-=taken;return taken;}
    };}
    public static void run(ServerLevel level)throws Exception{
        int index=0;
        for(var suite:java.util.List.of(RuneTransitGameTests.class,RuneAggregateTransitGameTests.class))for(var method:suite.getDeclaredMethods()){
            if(!java.lang.reflect.Modifier.isPublic(method.getModifiers())||method.getParameterCount()!=1||method.getParameterTypes()[0]!=TransitTestWorld.class)continue;
            var world=new TransitTestWorld(level,new BlockPos(128+(index++)*96,100,128));
            try{method.invoke(null,world);}catch(java.lang.reflect.InvocationTargetException failure){throw new AssertionError(method.getName(),failure.getCause());}
            com.mojang.logging.LogUtils.getLogger().info("FABRIC_RUNE_TRANSIT_CASE_OK {}",method.getName());
        }
        com.mojang.logging.LogUtils.getLogger().info("FABRIC_RUNE_TRANSIT_OK cases={} native-items native-fluids native-energy source-fixture",index);
    }
}
