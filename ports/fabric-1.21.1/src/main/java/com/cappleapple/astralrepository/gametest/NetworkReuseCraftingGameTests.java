package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.CrystalNodeBlockEntity;
import com.cappleapple.astralrepository.crafting.CraftingService;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NetworkReuseCraftingGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=600)
    public static void distantNexusPlacementAndRemovalPreserveAnActiveCraft(GameTestHelper helper) {
        BlockPos nexus=new BlockPos(5,2,5),chest=nexus.west(),table=nexus.east(),shelf=nexus.north(),distant=new BlockPos(20,2,20);
        helper.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());helper.setBlock(chest,Blocks.CHEST);
        helper.setBlock(table,Blocks.CRAFTING_TABLE);helper.setBlock(shelf,Blocks.CHISELED_BOOKSHELF);
        Container storage=(Container)helper.getBlockEntity(chest);storage.setItem(0,new ItemStack(Items.IRON_INGOT,4));
        ItemStack tome=new ItemStack(AstralContent.RECIPE_TOME.get());CompoundTag data=new CompoundTag();
        data.put("Output",new ItemStack(Items.IRON_TRAPDOOR).save(helper.getLevel().registryAccess()));data.putString("OutputId","minecraft:iron_trapdoor");
        tome.set(DataComponents.CUSTOM_DATA,CustomData.of(data));((Container)helper.getBlockEntity(shelf)).setItem(0,tome);
        var manager=NetworkManager.get(helper.getLevel().getServer());
        var address=((CrystalNodeBlockEntity)helper.getBlockEntity(nexus)).address();
        var farAddress=AnchorAddress.crystal(helper.getLevel(),helper.absolutePos(distant));
        GlobalPos tablePosition=GlobalPos.of(helper.getLevel().dimension(),helper.absolutePos(table));
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"reuse-craft"));
        player.setPos(helper.absolutePos(nexus).getCenter());
        AstralNetwork[] original={null};UUID[] job={null};ItemKey iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
        helper.startSequence().thenWaitUntil(()->{
            var network=manager.networkAt(address);
            helper.assertTrue(network!=null&&network.snapshot().getOrDefault(iron,0L)==4&&network.workstations().contains(tablePosition)&&!network.exposedProducts().isEmpty(),"Real table, chest and recipe shelf are discovered");
        }).thenExecute(()->{
            original[0]=manager.networkAt(address);
            var request=original[0].crafting().request(player,new ItemStack(Items.IRON_TRAPDOOR),1);
            helper.assertTrue(request.accepted(),"Real table craft starts: "+request.message());job[0]=request.jobId();
        }).thenWaitUntil(()->helper.assertTrue(CraftingService.isProcessorReserved(tablePosition),"The original craft owns its physical table"))
        .thenExecute(()->helper.setBlock(distant,AstralContent.STORAGE_NEXUS.get()))
        .thenWaitUntil(()->helper.assertTrue(manager.networkAt(farAddress)!=null,"The distant unlinked Nexus joins the manager"))
        .thenExecute(()->{
            helper.assertTrue(manager.networkAt(address)==original[0],"Distant placement preserves the original network and its index");
            helper.assertTrue(original[0].crafting().statuses().stream().anyMatch(s->s.id().equals(job[0])&&!s.state().equals("CANCELLED")),"Placement does not cancel the unrelated craft");
            helper.setBlock(distant,Blocks.AIR);
        }).thenWaitUntil(()->helper.assertTrue(manager.networkAt(farAddress)==null,"The distant Nexus is removed from membership"))
        .thenExecute(()->helper.assertTrue(manager.networkAt(address)==original[0],"Distant removal preserves the original coordinator"))
        .thenWaitUntil(()->helper.assertTrue(original[0].crafting().statuses().stream().anyMatch(s->s.id().equals(job[0])&&s.state().equals("COMPLETE")),"The original job completes without being resubmitted"))
        .thenExecute(()->{
            long outputs=0,inputs=0;for(int i=0;i<storage.getContainerSize();i++){ItemStack stack=storage.getItem(i);if(stack.is(Items.IRON_TRAPDOOR))outputs+=stack.getCount();if(stack.is(Items.IRON_INGOT))inputs+=stack.getCount();}
            helper.assertTrue(outputs==1&&inputs==0,"The same craft returns exactly one physical trapdoor and consumes four ingots");
            helper.assertTrue(!CraftingService.isProcessorReserved(tablePosition),"Completed work releases the processor");
        }).thenSucceed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=200)
    public static void craftingReturnsSkipOtherIdleProcessingMachines(GameTestHelper helper){
        var nexus=new BlockPos(5,2,5);var source=nexus.west(2).north(2);var furnace=nexus.west();var blast=furnace.south();var smoker=furnace.south(2);var chest=nexus.east(4);
        helper.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());helper.setBlock(source,Blocks.FURNACE);helper.setBlock(furnace,Blocks.FURNACE);helper.setBlock(blast,Blocks.BLAST_FURNACE);helper.setBlock(smoker,Blocks.SMOKER);helper.setBlock(chest,Blocks.CHEST);
        var manager=NetworkManager.get(helper.getLevel().getServer());var address=((CrystalNodeBlockEntity)helper.getBlockEntity(nexus)).address();
        helper.startSequence().thenWaitUntil(()->{
            var network=manager.networkAt(address);helper.assertTrue(network!=null&&network.storageCount()>=5,"Return storage and every idle furnace are discovered");
        }).thenExecute(()->{
            var network=manager.networkAt(address);
            var preferred=com.cappleapple.astralrepository.content.RuneSurfaces.getOrCreate(helper.getLevel(),helper.absolutePos(furnace),Direction.UP);
            preferred.addLayer(com.cappleapple.astralrepository.content.RuneGlyph.id(com.cappleapple.astralrepository.content.RuneLayer.Mode.FILTER),com.cappleapple.astralrepository.content.RuneLayer.Mode.FILTER).setPriority(99);
            var returned=network.insertFrom(new ItemStack(Items.MINER_POTTERY_SHERD,64),GlobalPos.of(helper.getLevel().dimension(),helper.absolutePos(source)));
            helper.assertTrue(returned.isEmpty(),"A completed craft can return its whole stack to actual storage");
            Container storage=(Container)helper.getBlockEntity(chest);long outputs=0;for(int slot=0;slot<storage.getContainerSize();slot++)if(storage.getItem(slot).is(Items.MINER_POTTERY_SHERD))outputs+=storage.getItem(slot).getCount();
            helper.assertTrue(outputs==64,"Completed output belongs in storage, not an unrelated idle machine");
            for(var pos:java.util.List.of(source,furnace,blast,smoker)){Container machine=(Container)helper.getBlockEntity(pos);for(int slot=0;slot<machine.getContainerSize();slot++)helper.assertTrue(machine.getItem(slot).isEmpty(),"Craft returns must leave processor slots available for later orders");}
        }).thenSucceed();
    }
    private NetworkReuseCraftingGameTests() {}
}
