package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.RuneSurface;
import com.cappleapple.astralrepository.network.AnchorAddress;
import com.cappleapple.astralrepository.network.RuneSavedData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class RuneSavedDataGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void nearbyFacesRespectRadiusAndLiveHostRemoval(GameTestHelper h){
        var level=h.getLevel();var a=h.absolutePos(new BlockPos(1,1,1));var b=a.east(3);
        level.setBlockAndUpdate(a,net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(b,net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
        var near=com.cappleapple.astralrepository.content.RuneSurfaces.getOrCreate(level,a,Direction.NORTH);
        var far=com.cappleapple.astralrepository.content.RuneSurfaces.getOrCreate(level,b,Direction.NORTH);
        var found=com.cappleapple.astralrepository.content.RuneSurfaces.nearby(level,a.getCenter(),1);
        h.assertTrue(found.contains(near)&&!found.contains(far),"Nearby bucket query applies physical distance");
        level.setBlockAndUpdate(a,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.assertTrue(!com.cappleapple.astralrepository.content.RuneSurfaces.nearby(level,a.getCenter(),1).contains(near),"Removed hosts disappear without waiting for index reconciliation");
        h.assertTrue(com.cappleapple.astralrepository.content.RuneSurfaces.nearby(level,a.getCenter(),4).contains(far),"The neighboring loaded host remains discoverable");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void surfaceIndexesKeepImmutableSnapshotsAcrossReplacementRemovalAndReload(GameTestHelper h){
        var server=h.getLevel().getServer();var data=new RuneSavedData();
        var position=GlobalPos.of(h.getLevel().dimension(),new BlockPos(31,80,31));
        var nextChunk=GlobalPos.of(h.getLevel().dimension(),new BlockPos(32,80,31));
        var otherDimension=GlobalPos.of(Level.NETHER,position.pos());
        var first=new RuneSurface(server,new AnchorAddress(position,Direction.NORTH));
        var second=new RuneSurface(server,new AnchorAddress(position,Direction.SOUTH));
        var distant=new RuneSurface(server,new AnchorAddress(nextChunk,Direction.NORTH));
        var dimensional=new RuneSurface(server,new AnchorAddress(otherDimension,Direction.NORTH));
        data.addSurface(first);var snapshot=data.surfaces(position);
        h.assertTrue(snapshot==data.surfaces(position),"Repeated reads reuse the immutable position snapshot");
        data.addSurface(second);data.addSurface(distant);data.addSurface(dimensional);
        h.assertTrue(snapshot.equals(List.of(first))&&data.surfaces(position).size()==2,"Adding a face preserves old snapshots and updates only the live index");
        h.assertTrue(data.surfaces(position.dimension(),new ChunkPos(position.pos())).size()==2,"Chunk index excludes neighboring chunks and other dimensions");
        var replacement=new RuneSurface(server,first.address());data.addSurface(replacement);
        h.assertTrue(data.surfaces(position).contains(replacement)&&!data.surfaces(position).contains(first)&&data.surfaces().size()==4,"Replacing an address removes the prior instance from every index");
        h.assertTrue(data.removeSurface(second.address())&&data.surfaces(position).equals(List.of(replacement)),"Removing a face updates its position index");
        var restored=RuneSavedData.load(server,data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.surfaces().size()==3&&restored.surfaces(position).size()==1&&restored.surfaces(otherDimension).size()==1,"Loading saved surfaces reconstructs dimension-aware indexes");
        h.assertTrue(restored.surfaces(position.dimension(),new ChunkPos(nextChunk.pos())).size()==1,"Loading reconstructs chunk indexes");
        boolean immutable=false;try{restored.surfaces(position).clear();}catch(UnsupportedOperationException expected){immutable=true;}
        h.assertTrue(immutable,"Position callers cannot mutate the saved-data index");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void adjacencyIndexTracksToggleRemovalAndReloadWithoutScanningUnrelatedLinks(GameTestHelper h){
        var data=new RuneSavedData();var dimension=h.getLevel().dimension();
        var a=new AnchorAddress(GlobalPos.of(dimension,new BlockPos(0,80,0)),Direction.NORTH);
        var b=new AnchorAddress(GlobalPos.of(dimension,new BlockPos(3,80,0)),null);
        var c=new AnchorAddress(GlobalPos.of(dimension,new BlockPos(6,80,0)),null);
        var d=new AnchorAddress(GlobalPos.of(dimension,new BlockPos(9,80,0)),null);
        data.toggle(a,b);data.toggle(b,c);data.toggle(c,d);var old=data.links(b);
        h.assertTrue(old==data.links(b)&&old.size()==2,"Repeated neighbor lookups reuse one immutable adjacency set");
        h.assertTrue(!data.toggle(a,b)&&data.links(a).isEmpty()&&data.links(b).equals(java.util.Set.of(c)),"Toggling removes both directions and empty buckets");
        h.assertTrue(old.size()==2,"Previously returned adjacency remains immutable snapshot data");
        var restored=RuneSavedData.load(h.getLevel().getServer(),data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.links(c).equals(java.util.Set.of(b,d)),"Loading reconstructs undirected neighbor indexes");
        restored.removeLinks(b);
        h.assertTrue(restored.links(b).isEmpty()&&restored.links(c).equals(java.util.Set.of(d))&&restored.links().size()==1,"Removing one anchor preserves unrelated links");h.succeed();
    }
}
