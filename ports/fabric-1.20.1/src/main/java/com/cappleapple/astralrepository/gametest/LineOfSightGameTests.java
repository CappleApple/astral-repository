package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class LineOfSightGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="line_of_sight",timeoutTicks=100)
    public static void opaqueEditsBlockRoutesTransparentBlocksPassAndRelayDetoursRecover(GameTestHelper h)throws Exception{
        boolean previous=AstralConfig.requireLineOfSight.get();int range=AstralConfig.relayRange.get();
        try{
            AstralConfig.requireLineOfSight.set(true);AstralConfig.relayRange.set(16);
            var level=h.getLevel();var manager=NetworkManager.get(level.getServer());var tick=NetworkManager.class.getDeclaredMethod("tick");tick.setAccessible(true);
            BlockPos a=new BlockPos(1,4,3),b=new BlockPos(13,4,3),wall=new BlockPos(7,4,3),detour=new BlockPos(7,4,12);
            for(var p:List.of(a,b,wall,detour))level.getChunkAt(h.absolutePos(p));
            h.setBlock(a,AstralContent.STORAGE_NEXUS.get());h.setBlock(b,AstralContent.RELAY_CRYSTAL.get());
            var first=(CrystalNodeBlockEntity)h.getBlockEntity(a);var second=(CrystalNodeBlockEntity)h.getBlockEntity(b);first.setChannel(11);second.setChannel(11);tick.invoke(manager);
            var from=GlobalPos.of(level.dimension(),h.absolutePos(a));var to=GlobalPos.of(level.dimension(),h.absolutePos(b));
            h.assertTrue(manager.networkAt(from)==manager.networkAt(to),"Clear relays automatically join");
            h.assertTrue(manager.route(from,to,11,16).size()==2,"Clear direct leg is used");
            h.assertTrue(manager.route(from,to,11,4).size()==2,"Relay range remains separate from endpoint attachment range");
            h.setBlock(wall,Blocks.STONE);
            h.assertTrue(manager.route(from,to,11,16).isEmpty(),"Opaque block invalidates the previously cached route immediately");
            tick.invoke(manager);h.assertTrue(manager.networkAt(from)!=manager.networkAt(to),"Opaque wall separates automatic networks");
            h.setBlock(wall,Blocks.CHEST);
            h.assertTrue(!LineOfSight.clear(level,h.absolutePos(a),h.absolutePos(b),ignored->{})&&manager.route(from,to,11,16).isEmpty(),"A chest is an opaque partial shape and blocks a ray through its interior");

            for(var transparent:List.of(Blocks.GLASS,Blocks.TINTED_GLASS,Blocks.GLASS_PANE,Blocks.OAK_LEAVES,Blocks.WATER)){
                h.setBlock(wall,transparent);tick.invoke(manager);
                h.assertTrue(manager.networkAt(from)==manager.networkAt(to)&&manager.route(from,to,11,16).size()==2,"Transparent block passes sight: "+transparent);
            }
            h.setBlock(wall,Blocks.STONE);h.setBlock(detour,AstralContent.RELAY_CRYSTAL.get());((CrystalNodeBlockEntity)h.getBlockEntity(detour)).setChannel(11);tick.invoke(manager);
            var path=manager.route(from,to,11,16);h.assertTrue(path.size()==3&&path.get(1).pos().equals(h.absolutePos(detour)),"Nearby blocked endpoints take a visible relay detour");
            h.setBlock(wall,Blocks.FURNACE);path=manager.route(from,to,11,16);
            h.setBlock(wall,Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT,true));
            h.assertTrue(manager.route(from,to,11,16)==path,"Furnace lighting with unchanged occlusion preserves warmed routes");
            h.setBlock(detour,Blocks.AIR);tick.invoke(manager);h.assertTrue(manager.route(from,to,11,16).isEmpty(),"Removed detour pauses the route");
            AstralConfig.requireLineOfSight.set(false);tick.invoke(manager);h.assertTrue(manager.route(from,to,11,16).size()==2,"Sight requirement can be disabled");
            h.assertTrue(manager.route(from,to,11,4).size()==2,"Sight-disabled relay route is cached independently");
            AstralConfig.requireLineOfSight.set(true);
            h.assertTrue(manager.route(from,to,11,4).isEmpty(),"Re-enabling sight before the next tick cannot reuse an unchecked route");
            AstralConfig.requireLineOfSight.set(true);AstralConfig.relayRange.set(4);h.setBlock(wall,Blocks.AIR);tick.invoke(manager);
            h.assertTrue(manager.route(from,to,11,4).isEmpty(),"Visible endpoints still obey the configured range");
            AstralConfig.relayRange.set(16);tick.invoke(manager);h.assertTrue(!manager.route(from,to,11,4).isEmpty(),"Increasing range reconnects the visible relays");
            h.succeed();
        }finally{AstralConfig.requireLineOfSight.set(previous);AstralConfig.relayRange.set(range);}
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="line_of_sight",timeoutTicks=100)
    public static void opaqueContainerTransfersPauseWithoutChangingCounts(GameTestHelper h)throws Exception{
        var level=h.getLevel();var manager=NetworkManager.get(level.getServer());var tick=NetworkManager.class.getDeclaredMethod("tick");tick.setAccessible(true);
        BlockPos nexus=new BlockPos(1,4,3),chest=new BlockPos(5,4,3),wall=new BlockPos(3,4,3);
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());((CrystalNodeBlockEntity)h.getBlockEntity(nexus)).setChannel(10);h.setBlock(chest,Blocks.CHEST);
        var box=(net.minecraft.world.Container)h.getBlockEntity(chest);box.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,16));
        for(int i=0;i<20;i++)tick.invoke(manager);var origin=GlobalPos.of(level.dimension(),h.absolutePos(nexus));var network=manager.networkAt(origin);network.invalidate(GlobalPos.of(level.dimension(),h.absolutePos(chest)));network.tick(4096,4096);
        var key=new com.cappleapple.astralrepository.api.ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        h.assertTrue(network.extractAt(key,1,origin).getCount()==1,"Clear storage withdrawal works");
        h.setBlock(wall,Blocks.STONE);
        h.assertTrue(network.extractAt(key,1,origin).isEmpty()&&box.getItem(0).getCount()==15,"Blocked withdrawal leaves contents untouched");
        h.assertTrue(network.insertAt(key.sample(),origin).getCount()==1&&box.getItem(0).getCount()==15,"Blocked deposit returns the offered item untouched");
        h.setBlock(wall,Blocks.GLASS);h.assertTrue(network.extractAt(key,1,origin).getCount()==1&&box.getItem(0).getCount()==14,"Replacing wall with glass restores transfer without waiting for rediscovery");
        h.succeed();
    }
}
