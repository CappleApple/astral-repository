package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.network.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;

public final class RuneSurfaces {
    public static BiConsumer<ServerLevel,BlockPos> onChanged=(level,pos)->{};
    private RuneSurfaces(){}
    public static RuneSurface get(ServerLevel level,BlockPos pos,Direction face){return RuneSavedData.get(level.getServer()).surface(AnchorAddress.rune(level,pos,face));}
    public static RuneSurface getOrCreate(ServerLevel level,BlockPos pos,Direction face){
        if(!level.hasChunkAt(pos)||level.getBlockEntity(pos)==null)throw new IllegalArgumentException("Rune host must be a loaded storage block");
        RuneSurface result=get(level,pos,face);if(result!=null&&!result.isRemoved())return result;if(result!=null)remove(level,pos,face);
        result=new RuneSurface(level.getServer(),AnchorAddress.rune(level,pos,face));RuneSavedData.get(level.getServer()).addSurface(result);result.topologyChanged();return result;
    }
    public static boolean remove(ServerLevel level,BlockPos pos,Direction face){boolean removed=RuneSavedData.get(level.getServer()).removeSurface(AnchorAddress.rune(level,pos,face));if(removed){onChanged.accept(level,pos);NetworkManager.changed(level,pos);}return removed;}
    public static List<RuneSurface> at(ServerLevel level,BlockPos pos){return RuneSavedData.get(level.getServer()).surfaces(GlobalPos.of(level.dimension(),pos));}
    /** Loaded nearby faces from spatial buckets; player updates never enumerate every world rune. */
    public static List<RuneSurface> nearby(ServerLevel level,net.minecraft.world.phys.Vec3 center,int radius){
        if(radius<0||radius>128)throw new IllegalArgumentException("Rune view radius must be 0..128");
        var data=RuneSavedData.get(level.getServer());var result=new ArrayList<RuneSurface>();
        int minX=net.minecraft.util.Mth.floor(center.x-radius)>>4,maxX=net.minecraft.util.Mth.floor(center.x+radius)>>4;
        int minZ=net.minecraft.util.Mth.floor(center.z-radius)>>4,maxZ=net.minecraft.util.Mth.floor(center.z+radius)>>4;
        double squared=(double)radius*radius;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            if(!level.hasChunk(x,z))continue;
            for(var surface:data.surfaces(level.dimension(),new net.minecraft.world.level.ChunkPos(x,z)))
                if(center.distanceToSqr(surface.getBlockPos().getCenter())<=squared&&!surface.isRemoved())result.add(surface);
        }
        return result;
    }
    public static List<RuneSurface> loaded(ServerLevel level){return RuneSavedData.get(level.getServer()).surfaces().stream().filter(r->r.getLevel()==level&&!r.isRemoved()).toList();}
}