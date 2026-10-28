package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** No explicit network invalidation: ordinary world edits must refresh a completed coverage scan. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class ProcessorDiscoveryGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=1800)
    public static void latePlacedVanillaFurnaceIsDiscoveredAndRunsThroughTheActualNetwork(GameTestHelper h){run(h,Blocks.FURNACE);}

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=1800)
    public static void latePlacedIronFurnaceIsDiscoveredAndRunsThroughTheActualNetwork(GameTestHelper h){
        if(!ModList.get().isLoaded("ironfurnaces")){
            h.assertTrue(!Boolean.getBoolean("astral_repository.recipeCompatTest"),"Integration gate requires Iron Furnaces");h.succeed();return;
        }
        run(h,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ironfurnaces:iron_furnace")));
    }
    private static void run(GameTestHelper h,Block furnace){
        BlockPos nexus=new BlockPos(5,2,5),chest=nexus.west(),shelf=nexus.north(),table=nexus.east(),machine=nexus.south();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);h.setBlock(shelf,Blocks.CHISELED_BOOKSHELF);h.setBlock(table,Blocks.CRAFTING_TABLE);
        var storage=(Container)h.getBlockEntity(chest);storage.setItem(0,new ItemStack(Items.RAW_IRON));storage.setItem(1,new ItemStack(Items.COAL));
        ItemStack tome=new ItemStack(AstralContent.RECIPE_TOME.get());CompoundTag tag=new CompoundTag();
        tag.put("Output",new ItemStack(Items.IRON_INGOT).save(h.getLevel().registryAccess()));tag.putString("OutputId","minecraft:iron_ingot");tome.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        ((Container)h.getBlockEntity(shelf)).setItem(0,tome);
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"late-furnace"));var absolute=h.absolutePos(nexus);player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        GlobalPos origin=GlobalPos.of(h.getLevel().dimension(),absolute),processor=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(machine));
        var manager=NetworkManager.get(h.getLevel().getServer());
        h.startSequence().thenWaitUntil(()->{
            var network=manager.networkAt(origin);h.assertTrue(network!=null,"Network exists");
            h.assertTrue(scanComplete(network),"Initial coverage scan is complete before placing the machine");
            h.assertTrue(network.snapshot().getOrDefault(key(Items.RAW_IRON),0L)==1&&network.exposedProducts().stream().anyMatch(s->s.is(Items.IRON_INGOT)),"Actual ingredients and taught output are indexed");
        }).thenExecute(()->h.setBlock(machine,furnace)).thenWaitUntil(()->{
            var network=manager.networkAt(origin);h.assertTrue(network.workstations().contains(processor),"Late processor discovered without test invalidation");
        }).thenExecute(()->{
            var request=manager.networkAt(origin).crafting().request(player,new ItemStack(Items.IRON_INGOT),1);h.assertTrue(request.accepted(),"Network smelting request accepted: "+request.message());
        }).thenWaitUntil(()->{
            var network=manager.networkAt(origin);h.assertTrue(network.crafting().activeJobs()==0&&network.snapshot().getOrDefault(key(Items.IRON_INGOT),0L)==1,"Actual network smelt completed: "+network.crafting().statuses());
            h.assertTrue(network.snapshot().getOrDefault(key(Items.RAW_IRON),0L)==0,"Real raw input consumed");
            h.assertTrue(!com.cappleapple.astralrepository.crafting.CraftingService.isProcessorReserved(processor),"Processor claim released");
        }).thenSucceed();
    }
    private static boolean scanComplete(AstralNetwork network){
        try{var field=AstralNetwork.class.getDeclaredField("scans");field.setAccessible(true);return ((Collection<?>)field.get(network)).isEmpty();}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static ItemKey key(Item item){return new ItemKey(new ItemStack(item));}
    private ProcessorDiscoveryGameTests(){}
}
