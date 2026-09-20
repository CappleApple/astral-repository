package com.cappleapple.astralrepository.network;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;

/** Loaded-block visibility. Transparent materials never obstruct; partial opaque shapes obstruct only where hit. */
public final class LineOfSight {
    public static boolean clear(ServerLevel level,BlockPos from,BlockPos to,Consumer<BlockPos> visited){
        if(!level.hasChunkAt(from)||!level.hasChunkAt(to))return false;
        var start=from.getCenter();var end=to.getCenter();
        return BlockGetter.traverseBlocks(start,end,level,(world,pos)->{
            visited.accept(pos);
            if(!world.hasChunkAt(pos))return Boolean.FALSE;
            if(pos.equals(from)||pos.equals(to))return null;
            var state=world.getBlockState(pos);
            if(!state.canOcclude())return null;
            var shape=state.getOcclusionShape(world,pos);
            return !shape.isEmpty()&&shape.clip(start,end,pos)!=null?Boolean.FALSE:null;
        },world->Boolean.TRUE);
    }
    private LineOfSight(){}
}
