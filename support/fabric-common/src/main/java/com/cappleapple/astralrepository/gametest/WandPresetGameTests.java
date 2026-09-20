package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import com.cappleapple.astralrepository.AstralServerConfig;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository") @PrefixGameTestTemplate(false)
public final class WandPresetGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void exactRgbArtworkAndLegacyPaletteBothSurviveSaving(GameTestHelper h)throws Exception{
        int[] colors=new int[128*128];for(int i=0;i<colors.length;i++)colors[i]=0xff000000|(i*7919)&0xffffff;
        var design=new RuneDesign(128,colors,List.of());var preset=new RunePreset(UUID.randomUUID(),"Exact RGB",RuneLayer.Mode.FILTER,design,new FilterRules().save(h.getLevel().registryAccess()),0,true);
        var copy=RunePresetFiles.decode(RunePresetFiles.encode(preset),h.getLevel().registryAccess());h.assertTrue(Arrays.equals(colors,copy.design().argbPixels()),"All 16384 RGB pixels survive portable JSON");
        h.assertTrue(Arrays.equals(colors,RuneDesign.load(design.save()).argbPixels()),"Exact RGB survives world NBT");
        var old=new CompoundTag();old.putInt("Size",1);old.putByteArray("Pixels",new byte[]{45});h.assertTrue(RuneDesign.load(old).argb(0,0)==RuneDesign.color(45),"Legacy indexed world art keeps its exact displayed color");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void paintedPresetPlacementPersistsIndependentCopiesAndNearestFreePositions(GameTestHelper h)throws Exception {
        BlockPos pos=h.absolutePos(new BlockPos(3,2,3));h.getLevel().setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"preset-placement"));player.setPos(pos.getCenter().add(0,0,2));
        byte[] pixels=new byte[64*64];pixels[63*64+63]=45;pixels[3*64+5]=99;var icon=new RuneDesign.Icon(Identifier.withDefaultNamespace("diamond"),12,15,10,45);var design=new RuneDesign(64,pixels,List.of(icon));FilterRules filter=new FilterRules();filter.add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);filter.setTarget(32);
        var preset=new RunePreset(UUID.randomUUID(),"Saved rune",RuneLayer.Mode.PULL,design,filter.save(h.getLevel().registryAccess()),7,true);var decoded=RunePresetFiles.decode(RunePresetFiles.encode(preset),h.getLevel().registryAccess());h.assertTrue(decoded.design().key().equals(design.key())&&decoded.filter().equals(preset.filter()),"Portable JSON preserves hidden pixels, item transforms, and behavior");
        var library=RuneLibraryData.get(h.getLevel().getServer()).library(player.getUUID());library.put(preset.id(),preset);ItemStack wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());WandPackets.selection(wand,preset.id(),true);player.setItemInHand(InteractionHand.MAIN_HAND,wand);
        var surface=RuneSurfaces.getOrCreate(h.getLevel(),pos,Direction.UP);var frame=RuneLayout.frame(h.getLevel(),pos,Direction.UP);Vec3 point=frame.center().center().add(frame.center().right().scale(.30)).add(frame.center().up().scale(.20));
        for(int i=0;i<4;i++){RuneProgramming.use(wand,h.getLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(frame.center().center().add(frame.center().right().scale(i%2==0?-.22:.22)).add(frame.center().up().scale(i<2?.22:-.22)),Direction.UP,pos,false));}
        h.assertTrue(surface.layers().size()==4,"Four placements fit without overlap");var cells=RuneLayout.placed(surface);for(int i=0;i<cells.size();i++)for(int j=i+1;j<cells.size();j++){var d=cells.get(i).center().subtract(cells.get(j).center());h.assertTrue(Math.abs(d.dot(cells.get(i).right()))>=cells.get(i).size()||Math.abs(d.dot(cells.get(i).up()))>=cells.get(i).size(),"Occupied rune squares never overlap");}
        library.remove(preset.id());var loaded=RuneSurface.load(h.getLevel().getServer(),surface.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(loaded.layers().size()==4&&loaded.layers().getFirst().design().pixel(63,63)==45&&loaded.layers().getFirst().design().icons().equals(List.of(icon)),"Placed design survives deletion and reload without flattening item layers");h.assertTrue(loaded.layers().getFirst().filter().target()==32&&loaded.layers().getFirst().priority()==7,"Placed behavior survives deletion and reload");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{var action=new WandPackets.Action(UUID.randomUUID(),WandPackets.Op.IMPORT,preset.id(),preset.save());WandPackets.Action.CODEC.encode(buffer,action);h.assertTrue(WandPackets.Action.CODEC.decode(buffer).data().equals(action.data()),"Actual bounded editor codec retains the preset");}finally{buffer.release();}h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void pushAndPullUseWholeNetworkWithoutMovingBackIntoTheirOwnHost(GameTestHelper h){
        // Keep the pull host outside Nexus coverage without placing the opaque source chest in its sight line.
        BlockPos source=new BlockPos(3,2,3),nexus=new BlockPos(7,2,3),store=new BlockPos(9,2,3),out=new BlockPos(1,2,6);
        for(var p:List.of(source,store,out))h.setBlock(p,Blocks.CHEST);h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());var a=(ChestBlockEntity)h.getBlockEntity(source);var b=(ChestBlockEntity)h.getBlockEntity(store);var c=(ChestBlockEntity)h.getBlockEntity(out);a.setItem(0,new ItemStack(Items.IRON_INGOT,40));
        var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(source),Direction.SOUTH);var push=surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);push.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);h.assertTrue(surface.toggleTarget(push.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus)),null).success(),"Wand network endpoint is valid without its own inventory");var editor=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"network-rune-settings"));editor.setPos(h.absolutePos(source).getCenter().add(0,0,2));RuneSettingsPackets.open(editor,surface,push);
        h.assertTrue(out.distSqr(nexus)>Math.pow(com.cappleapple.astralrepository.AstralConfig.coverageRange.get(),2),"Pull host is outside automatic Nexus storage discovery");
        h.assertTrue(LineOfSight.clear(h.getLevel(),h.absolutePos(out),h.absolutePos(nexus),ignored->{})&&LineOfSight.clear(h.getLevel(),h.absolutePos(out),h.absolutePos(store),ignored->{}),"Pull has direct clear sight to both its network target and the stored items");
        h.onEachTick(()->h.assertTrue(count(a)+count(b)+count(c)==40,"Every tick conserves the source items"));
        h.startSequence().thenWaitUntil(()->h.assertTrue(count(b)==40&&count(a)==0,"Push filled another network store and did not reinsert into its own indexed host")).thenExecute(()->{push.setEnabled(false);var destination=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(out),Direction.SOUTH);var pull=destination.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PULL),RuneLayer.Mode.PULL);pull.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);pull.filter().setTarget(24);h.assertTrue(destination.toggleTarget(pull.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus)),null).success(),"Pull binds a network");}).thenWaitUntil(()->h.assertTrue(count(c)==24&&count(b)==16,"Pull stocks to its target from the network: source="+count(a)+", store="+count(b)+", output="+count(c)+", rune="+RuneSurfaces.get(h.getLevel(),h.absolutePos(out),Direction.SOUTH).layers().getFirst().status())).thenSucceed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void wandRelayTargetPushesAndPullsAcrossConnectedNetwork(GameTestHelper h){
        BlockPos source=new BlockPos(2,2,2),relay=new BlockPos(5,2,3),nexus=new BlockPos(8,2,3),store=new BlockPos(9,2,5);
        h.setBlock(source,Blocks.BARREL);h.setBlock(store,Blocks.BARREL);h.setBlock(relay,AstralContent.RELAY_CRYSTAL.get());h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());
        var input=(net.minecraft.world.Container)h.getBlockEntity(source);var output=(net.minecraft.world.Container)h.getBlockEntity(store);input.setItem(0,new ItemStack(Items.DIAMOND,11));
        var face=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(source),Direction.SOUTH);var rune=face.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
        rune.filter().add(FilterRules.Kind.ITEM,"minecraft:diamond",false,ItemStack.EMPTY);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"relay-binding-transfer"));var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());player.setItemInHand(InteractionHand.MAIN_HAND,wand);player.setShiftKeyDown(true);
        player.gameMode.useItemOn(player,h.getLevel(),wand,InteractionHand.MAIN_HAND,new BlockHitResult(RuneLayout.placed(face).getFirst().center(),Direction.SOUTH,h.absolutePos(source),false));
        player.gameMode.useItemOn(player,h.getLevel(),wand,InteractionHand.MAIN_HAND,new BlockHitResult(h.absolutePos(relay).getCenter(),Direction.UP,h.absolutePos(relay),false));
        h.assertTrue(rune.target()!=null&&rune.target().face()==null,"Actual wand binds relay as a network endpoint");
        h.startSequence().thenWaitUntil(()->h.assertTrue(output.getItem(0).is(Items.DIAMOND)&&output.getItem(0).getCount()==11&&input.isEmpty(),"Push through relay reaches network storage; status="+rune.status()))
            .thenExecute(()->{rune.setMode(RuneLayer.Mode.PULL);rune.filter().setTarget(7);})
            .thenWaitUntil(()->h.assertTrue(input.getItem(0).getCount()==7&&output.getItem(0).getCount()==4,"Pull through relay takes network stock; status="+rune.status())).thenSucceed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void consolidatedUpgradesPreserveStorageAndSavedRange(GameTestHelper h){
        BlockPos pos=new BlockPos(3,2,3),relayPos=pos.east(3);h.setBlock(pos,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(relayPos,AstralContent.RELAY_CRYSTAL.get());var storage=(CrystalNodeBlockEntity)h.getBlockEntity(pos);storage.inventory().insertItem(0,new ItemStack(Items.DIAMOND,23),false);long capacity=storage.capacity();h.assertTrue(!storage.upgradeStorage(3)&&storage.upgradeStorage(2)&&storage.capacity()>capacity,"Storage upgrades are sequential and increase capacity");h.assertTrue(storage.upgradeStorage(3)&&storage.inventory().getStackInSlot(0).getCount()==23,"Both in-place stages preserve contents");var saved=storage.saveWithFullMetadata(h.getLevel().registryAccess());var loaded=new CrystalNodeBlockEntity(h.absolutePos(pos),storage.getBlockState());loaded.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(loaded.storageTier()==3&&loaded.inventory().getStackInSlot(0).getCount()==23,"Dropped/reloaded storage keeps stage and contents");var relay=(CrystalNodeBlockEntity)h.getBlockEntity(relayPos);h.assertTrue(!relay.longRange(),"Relay starts local");relay.upgradeRange();relay.setDimensional(true);var copy=new CrystalNodeBlockEntity(h.absolutePos(relayPos),relay.getBlockState());copy.loadWithComponents(relay.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(copy.longRange()&&copy.dimensional(),"Range and dimensional stages persist");h.assertTrue(AstralContent.activeNodes().size()==4,"Only four block roles are exposed");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void distantAssignmentWaitsForARelayRoute(GameTestHelper h){
        BlockPos a=new BlockPos(2,2,2),b=a.east(6);h.setBlock(a,Blocks.CHEST);h.setBlock(b,Blocks.CHEST);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"binding-range"));var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(a),Direction.SOUTH);var layer=surface.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);var wand=new ItemStack(AstralContent.ATTUNEMENT_WAND.get());
        var from=new RuneProgramming.Selection(surface.address(),layer.id());var to=new RuneProgramming.Selection(AnchorAddress.rune(h.getLevel(),h.absolutePos(b),Direction.SOUTH),null);double previous=AstralServerConfig.wandBindingRange.get();
        try{AstralServerConfig.wandBindingRange.set(4.0);RuneProgramming.link(wand,h.getLevel(),from,player);RuneProgramming.link(wand,h.getLevel(),to,player);h.assertTrue(layer.target()!=null,"Assignments persist while waiting for a relay route");h.assertTrue(NetworkManager.get(h.getLevel().getServer()).route(surface.address().position(),to.address().position(),surface.channel(),4).isEmpty(),"Distant endpoints cannot transfer without relays");}finally{AstralServerConfig.wandBindingRange.set(previous);}h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=500)
    public static void networkRuneRefundsReturnItemsAndFluidsWithoutReplay(GameTestHelper h){
        BlockPos pos=new BlockPos(3,2,3);h.setBlock(pos,AstralContent.SEED_STORAGE_CRYSTAL.get());var node=(CrystalNodeBlockEntity)h.getBlockEntity(pos);var address=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(pos));var recovery=TransferRecoveryData.get(h.getLevel().getServer());String provider="rune:"+UUID.randomUUID()+":astral_network";
        recovery.put(address,new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(Items.IRON_INGOT)),9,false,provider,null);
        recovery.put(address,new com.cappleapple.astralrepository.api.FluidKey(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1)),1000,false,provider+"_fluid",null);
        h.onEachTick(()->h.assertTrue(node.inventory().getStackInSlot(0).getCount()<=9&&node.tank().getFluidAmount()<=1000,"Confirmed remainders never duplicate"));
        h.startSequence().thenWaitUntil(()->h.assertTrue(node.inventory().getStackInSlot(0).getCount()==9&&node.tank().getFluidAmount()==1000,"Network endpoint recovers its own native store and tank")).thenIdle(40).thenExecute(()->h.assertTrue(recovery.confirmed().stream().noneMatch(e->e.provider().startsWith(provider)),"Recovered journal entries removed once")).thenSucceed();
    }
    private static int count(ChestBlockEntity c){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(Items.IRON_INGOT))n+=c.getItem(i).getCount();return n;}
}
