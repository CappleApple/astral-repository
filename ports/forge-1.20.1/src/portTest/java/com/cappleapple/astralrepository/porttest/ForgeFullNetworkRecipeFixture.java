package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.menu.*;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;

/** Exercises the real network's discovery, knowledge, reservation, routing, and machine execution. */
final class ForgeFullNetworkRecipeFixture {
    private static final BlockPos NEXUS=new BlockPos(64,100,1),STORE=NEXUS.east(2),SHELF=NEXUS.south(2),MACHINE=NEXUS.east(4);
    private static ServerLevel level;
    private static FakePlayer player;
    private static NexusMenu menu;
    private static boolean plain,placed,requested,done;
    private static int ticks,placedAt;
    static void setup(MinecraftServer server,boolean plainProfile){
        plain=plainProfile;level=server.overworld();
        net.minecraftforge.common.world.ForgeChunkManager.forceChunk(level,"astral_port_test",new UUID(0,2),4,0,true,true);
        level.getChunkAt(NEXUS);
        for(var pos:List.of(NEXUS,STORE,SHELF,MACHINE))level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(NEXUS,AstralContent.STORAGE_NEXUS.get().defaultBlockState());
        level.setBlockAndUpdate(STORE,AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
        level.setBlockAndUpdate(SHELF,Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        var nexus=(CrystalNodeBlockEntity)level.getBlockEntity(NEXUS);var storage=(CrystalNodeBlockEntity)level.getBlockEntity(STORE);
        nexus.setChannel(13);storage.setChannel(13);
        check(storage.inventory().insertItem(0,new ItemStack(Items.RAW_IRON),false).isEmpty(),"Full-network raw iron fits");
        check(storage.inventory().insertItem(1,new ItemStack(Items.COAL),false).isEmpty(),"Full-network coal fits");
        var manager=NetworkManager.get(server);
        check(manager.toggleLink(AnchorAddress.crystal(level,NEXUS),AnchorAddress.crystal(level,STORE)).success(),"Full-network crystal link established");
        player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"forge-full-network"));player.setPos(64.5,101,3.5);
        var tome=new ItemStack(AstralContent.RECIPE_TOME.get());player.setItemInHand(InteractionHand.MAIN_HAND,tome);
        check(new RecipeTomeMenu(310,player.getInventory(),InteractionHand.MAIN_HAND).inscribe(new ResourceLocation("minecraft:iron_ingot_from_smelting_raw_iron")),"Full-network Recipe Tome inscribed from the server recipe");
        ((Container)level.getBlockEntity(SHELF)).setItem(0,tome.copy());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        menu=new NexusMenu(311,player.getInventory(),at(NEXUS),false);player.containerMenu=menu;
        check(level.getBlockState(MACHINE).isAir(),"Late processor starts absent");
    }
    static boolean tick()throws ReflectiveOperationException{
        if(done)return true;
        if(++ticks>900)throw new AssertionError("Full-network discovery timeout; placed="+placed+" requested="+requested+" workstations="+(menu.network()==null?"none":menu.network().workstations())+" jobs="+(menu.network()==null?"none":menu.network().crafting().statuses()));
        var network=menu.network();if(network==null)return false;
        menu.broadcastChanges();
        if(!placed){
            if(!queueEmpty(network,"scans")||!queueEmpty(network,"dirty")||network.snapshot().getOrDefault(key(Items.RAW_IRON),0L)!=1||network.snapshot().getOrDefault(key(Items.COAL),0L)!=1||network.exposedProducts().stream().noneMatch(stack->stack.is(Items.IRON_INGOT)))return false;
            check(!network.workstations().contains(at(MACHINE)),"Absent processor was not in the completed scan");
            LogUtils.getLogger().info("FORGE_FULL_NETWORK_EMPTY_SCAN_OK ticks={} workstations={}",ticks,network.workstations());
            // Ordinary world mutation only: no explicit invalidation or custom workstation list.
            level.setBlockAndUpdate(MACHINE,plain?Blocks.FURNACE.defaultBlockState():BuiltInRegistries.BLOCK.get(new ResourceLocation("ironfurnaces:iron_furnace")).defaultBlockState());
            placed=true;placedAt=ticks;return false;
        }
        if(!requested){
            if(!network.workstations().contains(at(MACHINE)))return false;
            check(network.crafting().isProcessingPosition(at(MACHINE)),"Discovered furnace is a processor rather than output storage");
            menu.action(new NetworkPackets.Action(menu.containerId,NetworkPackets.CRAFT,new ItemStack(Items.IRON_INGOT),1,0,""));
            check(network.crafting().activeJobs()==1,"Real Nexus request accepted using its discovered library and processor");requested=true;
            LogUtils.getLogger().info("FORGE_FULL_NETWORK_LATE_PROCESSOR_DISCOVERED ticksAfterPlacement={}",ticks-placedAt);return false;
        }
        check(network.crafting().statuses().stream().noneMatch(job->job.state().equals("FAILED")),"Full-network craft failed: "+network.crafting().statuses());
        if(network.crafting().activeJobs()!=0)return false;
        check(network.snapshot().getOrDefault(key(Items.IRON_INGOT),0L)==1,"Full-network smelted iron returned to actual indexed storage");
        check(!network.snapshot().containsKey(key(Items.RAW_IRON))&&!network.snapshot().containsKey(key(Items.COAL)),"Full-network source ingredient and coal consumed exactly once");
        var machine=(Container)level.getBlockEntity(MACHINE);check(machine.getItem(0).isEmpty()&&machine.getItem(2).isEmpty(),"Full-network physical machine input/output settled");
        check(!com.cappleapple.astralrepository.crafting.CraftingService.isProcessorReserved(at(MACHINE)),"Full-network workstation reservation released");
        done=true;LogUtils.getLogger().info("FORGE_FULL_NETWORK_RECIPE_OK processor={} ticks={}",plain?"vanilla":"ironfurnaces",ticks);return true;
    }
    private static boolean queueEmpty(AstralNetwork network,String name)throws ReflectiveOperationException{Field field=AstralNetwork.class.getDeclaredField(name);field.setAccessible(true);return ((Collection<?>)field.get(network)).isEmpty();}
    private static GlobalPos at(BlockPos pos){return GlobalPos.of(level.dimension(),pos);}
    private static ItemKey key(Item item){return new ItemKey(new ItemStack(item));}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
