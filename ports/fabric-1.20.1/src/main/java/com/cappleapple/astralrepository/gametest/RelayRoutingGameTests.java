package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.cappleapple.astralrepository.api.ItemKey;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class RelayRoutingGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="relay_routing",timeoutTicks=100)
    public static void automaticMultipathRoutingAndNetworkTargetsConserveItems(GameTestHelper h)throws Exception {
        double range=AstralServerConfig.wandBindingRange.get();boolean instant=AstralConfig.instantAutomaticLogistics.get();
        try{
            AstralServerConfig.wandBindingRange.set(4.0);AstralConfig.instantAutomaticLogistics.set(true);
            var level=h.getLevel();var manager=NetworkManager.get(level.getServer());var tick=NetworkManager.class.getDeclaredMethod("tick");tick.setAccessible(true);
            BlockPos host=new BlockPos(1,2,3),target=new BlockPos(77,2,3),other=new BlockPos(74,2,6),detour=new BlockPos(32,2,9);
            for(var p:List.of(host,target,other)){level.getChunkAt(h.absolutePos(p));h.setBlock(p,Blocks.CHEST);}
            List<CrystalNodeBlockEntity> nodes=new ArrayList<>();
            for(int x:new int[]{4,18,32,46,60,74}){var p=new BlockPos(x,2,3);level.getChunkAt(h.absolutePos(p));h.setBlock(p,x==74?AstralContent.STORAGE_NEXUS.get():x==60?AstralContent.SEED_STORAGE_CRYSTAL.get():AstralContent.RELAY_CRYSTAL.get());var n=(CrystalNodeBlockEntity)h.getBlockEntity(p);n.setChannel(14);nodes.add(n);}
            level.getChunkAt(h.absolutePos(detour));h.setBlock(detour,AstralContent.RELAY_CRYSTAL.get());var alternate=(CrystalNodeBlockEntity)h.getBlockEntity(detour);alternate.setChannel(14);
            var surface=RuneSurfaces.getOrCreate(level,h.absolutePos(host),Direction.SOUTH);surface.setChannel(14);var rune=surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);rune.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);rune.filter().setTarget(16);
            var a=(ChestBlockEntity)h.getBlockEntity(host);var b=(ChestBlockEntity)h.getBlockEntity(target);var c=(ChestBlockEntity)h.getBlockEntity(other);a.setItem(0,new ItemStack(Items.IRON_INGOT,32));
            h.assertTrue(surface.toggleTarget(rune.id(),at(h,target),Direction.UP).success()&&surface.toggleTarget(rune.id(),at(h,other),Direction.NORTH).success(),"Multiple distant sided assignments accepted");
            tick.invoke(manager);
            var network=manager.networkAt(nodes.get(0).address());h.assertTrue(network!=null&&nodes.stream().allMatch(n->manager.networkAt(n.address())==network),"Relays, Nexus, and Storage Crystal join without saved bindings");
            h.assertTrue(manager.links(nodes.get(0).address()).isEmpty()&&manager.adjacent(nodes.get(1)).size()>=3,"Automatic graph retains multiple paths without saved edges");
            var route=manager.route(at(h,host),at(h,target),14,4);h.assertTrue(route.size()>=7&&route.get(0).equals(at(h,host))&&route.getLast().equals(at(h,target)),"Distant containers use ordered relay hops");
            var treesField=NetworkManager.class.getDeclaredField("routeTrees");treesField.setAccessible(true);var trees=treesField.get(manager);
            var entriesField=trees.getClass().getDeclaredField("entries");entriesField.setAccessible(true);var treeEntries=(Map<?,?>)entriesField.get(trees);
            var warmTrees=new HashMap<>(treeEntries);
            for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++)manager.route(at(h,host),at(h,target.offset(x,y,z)),14,4);
            h.assertTrue(warmTrees.equals(treeEntries),"Many destinations reuse the same origin search tree without retraversing the relay graph");
            h.assertTrue(manager.route(at(h,host),at(h,host.east(3)),14,4).size()==2,"In-range endpoints use the direct route");
            var restored=RuneSurface.load(level.getServer(),surface.save(level.registryAccess()),level.registryAccess());h.assertTrue(restored.get(rune.id()).targets().equals(rune.targets()),"Every target face survives persistence");
            var worker=new DirectRuneTransfers(level.getServer());worker.track(surface);for(int i=0;i<8;i++)worker.tick();
            h.assertTrue(count(a)==0&&count(b)==16&&count(c)==16,"Round-robin Push stocks both targets without duplication");
            h.setBlock(new BlockPos(32,2,3),Blocks.AIR);h.assertTrue(!manager.route(at(h,host),at(h,target),14,4).contains(at(h,new BlockPos(32,2,3))),"Cached route rejects a removed hop before queued topology refresh");NetworkManager.changed(level,h.absolutePos(new BlockPos(32,2,3)));tick.invoke(manager);
            h.assertTrue(manager.route(at(h,host),at(h,target),14,4).contains(at(h,detour)),"Removing one path selects the alternate relay");
            alternate.setChannel(13);tick.invoke(manager);h.assertTrue(manager.route(at(h,host),at(h,target),14,4).isEmpty(),"Wrong-channel bridge cannot connect the split network");
            a.setItem(0,new ItemStack(Items.IRON_INGOT,16));rune.filter().setTarget(32);for(int i=0;i<4;i++)worker.tick();h.assertTrue(count(a)==16&&count(b)+count(c)==32,"Disconnected transfers pause without moving or losing contents");
            rune.setEnabled(false);alternate.setChannel(14);tick.invoke(manager);h.assertTrue(!manager.route(at(h,host),at(h,target),14,4).isEmpty(),"Restoring the channel restores reachability");
            surface.clearTarget(rune.id());surface.toggleTarget(rune.id(),nodes.get(0).address().position(),null);rune.setMode(RuneLayer.Mode.PULL);rune.filter().setTarget(24);a.clearContent();rune.setEnabled(true);
            nodes.get(4).inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,24),false);
            // Inventory discovery is globally budgeted; unrelated fixture components must not determine readiness.
            var current=manager.networkAt(nodes.get(0).address());
            for(var position:List.of(at(h,host),at(h,target),at(h,other),nodes.get(4).address().position()))current.invalidate(position);
            current.tick(32,32);
            h.assertTrue(current.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)),0L)==56,"Fixture providers are discovered before checking aggregate transfer quantities");
            for(int i=0;i<40;i++)tick.invoke(manager);
            h.assertTrue(count(a)==24,"A Relay target pulls from storage across the connected network; host="+count(a)+", status="+rune.status()+", enabled="+rune.enabled()+", targets="+rune.targets()+", stores="+(current==null?-1:current.storageCount())+", indexed="+(current==null?Map.of():current.snapshot())+", seed="+nodes.get(4).inventory().getStackInSlot(0)+", route="+manager.route(at(h,host),nodes.get(4).address().position(),14,4));
            h.assertTrue(count(a)+count(b)+count(c)+nodes.get(4).inventory().getStackInSlot(0).getCount()==56,"Aggregate Relay target conserves all network items");
            surface.clearTarget(rune.id());surface.toggleTarget(rune.id(),nodes.getLast().address().position(),null);rune.setMode(RuneLayer.Mode.PUSH);rune.filter().setTarget(Long.MAX_VALUE);
            for(int i=0;i<40;i++)tick.invoke(manager);
            h.assertTrue(count(a)==0&&count(b)+count(c)+nodes.get(4).inventory().getStackInSlot(0).getCount()==56,"Nexus target pushes to the whole network without reinserting into its rune host");
            h.succeed();
        }finally{AstralServerConfig.wandBindingRange.set(range);AstralConfig.instantAutomaticLogistics.set(instant);}
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="async_relay_routing",timeoutTicks=100)
    public static void preparedRelayTreesRejectLiveRemovalsAndChannelChanges(GameTestHelper h)throws Exception{
        var level=h.getLevel();var manager=NetworkManager.get(level.getServer());
        var tick=NetworkManager.class.getDeclaredMethod("tick");tick.setAccessible(true);
        BlockPos first=new BlockPos(2,2,2),middle=new BlockPos(16,2,2),last=new BlockPos(30,2,2),detour=new BlockPos(16,2,7);
        for(var position:List.of(first,middle,last,detour)){
            level.getChunkAt(h.absolutePos(position));h.setBlock(position,AstralContent.RELAY_CRYSTAL.get());
            ((CrystalNodeBlockEntity)h.getBlockEntity(position)).setChannel(6);
        }
        level.getChunkAt(h.absolutePos(new BlockPos(32,2,2)));
        tick.invoke(manager);
        var end=at(h,new BlockPos(32,2,2));
        var initial=manager.route(at(h,new BlockPos(0,2,2)),end,6,3);
        h.assertTrue(initial.contains(at(h,middle)),"First query chooses the shortest relay path without waiting for a worker");
        var preparedField=NetworkManager.class.getDeclaredField("preparedRouteTrees");preparedField.setAccessible(true);
        var prepared=preparedField.get(manager);var request=prepared.getClass().getDeclaredMethod("request",Object.class);request.setAccessible(true);
        var origins=NetworkManager.class.getDeclaredField("preparedRouteOrigins");origins.setAccessible(true);
        h.startSequence().thenWaitUntil(()->{
            try{
                h.assertTrue(request.invoke(prepared,new AnchorAddress(at(h,first),null))!=null,"Current-revision relay-root computation completed on the worker");
            }catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
        }).thenExecute(()->{
            try{
                long before=origins.getLong(manager);
                var warm=manager.route(at(h,new BlockPos(0,3,2)),end,6,3);
                h.assertTrue(warm.contains(at(h,middle))&&origins.getLong(manager)>before,"A different origin uses the completed worker tree and keeps the shortest relay path");
                h.setBlock(middle,Blocks.AIR);
                var alternate=manager.route(at(h,new BlockPos(0,1,2)),end,6,3);
                h.assertTrue(!alternate.contains(at(h,middle))&&alternate.contains(at(h,detour)),"Prepared tree rejects a removed relay before queued topology refresh");
                ((CrystalNodeBlockEntity)h.getBlockEntity(detour)).setChannel(7);
                h.assertTrue(manager.route(at(h,new BlockPos(0,2,3)),end,6,3).isEmpty(),"Prepared tree cannot transfer through a relay retuned before graph refresh");
            }catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
        }).thenExecute(h::succeed);
    }

    private static GlobalPos at(GameTestHelper h,BlockPos p){return GlobalPos.of(h.getLevel().dimension(),h.absolutePos(p));}
    private static int count(ChestBlockEntity chest){int n=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(Items.IRON_INGOT))n+=chest.getItem(i).getCount();return n;}
}
