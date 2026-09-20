package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.*;
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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NetworkReuseBoundaryGameTests {
    private static GlobalPos at(GameTestHelper h,BlockPos pos){return GlobalPos.of(h.getLevel().dimension(),h.absolutePos(pos));}
    private static void changed(GameTestHelper h,BlockPos pos){NetworkManager.changed(h.getLevel(),h.absolutePos(pos));}
    private static long count(Container container,Item item){long count=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(item))count+=container.getItem(i).getCount();return count;}

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=700)
    public static void nearbyUnlinkedNexusInvalidatesBothOldAndNewOwnershipBoundaries(GameTestHelper h){
        BlockPos original=new BlockPos(5,2,5),chest=new BlockPos(9,2,5),nearer=new BlockPos(10,2,5);
        h.setBlock(original,AstralContent.STORAGE_NEXUS.get());h.setBlock(chest,Blocks.CHEST);((Container)h.getBlockEntity(chest)).setItem(0,new ItemStack(Items.IRON_INGOT,31));
        var manager=NetworkManager.get(h.getLevel().getServer());var first=at(h,original);var second=at(h,nearer);var key=new ItemKey(new ItemStack(Items.IRON_INGOT));
        AstralNetwork[] before={null},overlapped={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(manager.networkAt(first)!=null&&manager.networkAt(first).snapshot().getOrDefault(key,0L)==31,"The original Nexus owns and indexes the four-block-distant chest"))
                .thenExecute(()->{before[0]=manager.networkAt(first);h.setBlock(nearer,AstralContent.STORAGE_NEXUS.get());((CrystalNodeBlockEntity)h.getBlockEntity(nearer)).setChannel(3);changed(h,nearer);})
                .thenWaitUntil(()->{
                    var old=manager.networkAt(first);var fresh=manager.networkAt(second);
                    h.assertTrue(old!=null&&fresh!=null&&old!=fresh,"The unlinked nearer Nexus stays in its own component");
                    h.assertTrue(old!=before[0],"An unchanged address set is rebuilt when a new competing coverage owner appears");
                    h.assertTrue(fresh.snapshot().getOrDefault(key,0L)==31&&!old.snapshot().containsKey(key),"Only the new nearest owner indexes the physical chest");
                }).thenExecute(()->{overlapped[0]=manager.networkAt(first);h.setBlock(nearer,Blocks.AIR);changed(h,nearer);})
                .thenWaitUntil(()->{
                    var restored=manager.networkAt(first);
                    h.assertTrue(manager.networkAt(second)==null&&restored!=null&&restored!=overlapped[0],"Removing the competing owner invalidates the old overlap footprint too");
                    h.assertTrue(restored.snapshot().getOrDefault(key,0L)==31&&count((Container)h.getBlockEntity(chest),Items.IRON_INGOT)==31,"Original ownership returns without duplicating or moving chest contents");
                }).thenExecute(h::succeed);
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=800)
    public static void internalCycleEdgeEditsCancelJobsEvenWhenComponentMembershipIsUnchanged(GameTestHelper h){
        BlockPos a=new BlockPos(5,2,5),b=new BlockPos(9,2,5),c=new BlockPos(9,2,9),chest=a.west(),table=a.south().west(),shelf=a.north();
        h.setBlock(a,AstralContent.STORAGE_NEXUS.get());h.setBlock(b,AstralContent.POWER_NODE.get());h.setBlock(c,AstralContent.POWER_NODE.get());
        h.setBlock(chest,Blocks.CHEST);h.setBlock(table,Blocks.CRAFTING_TABLE);h.setBlock(shelf,Blocks.CHISELED_BOOKSHELF);
        for(var pos:java.util.List.of(b,c))((CrystalNodeBlockEntity)h.getBlockEntity(pos)).insertionFilter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);
        // A managed table has no native input inventory: refunded iron has only the chest as a destination.
        Container inventory=(Container)h.getBlockEntity(chest);inventory.setItem(0,new ItemStack(Items.IRON_INGOT,8));
        ItemStack tome=new ItemStack(AstralContent.RECIPE_TOME.get());CompoundTag tag=new CompoundTag();tag.put("Output",new ItemStack(Items.IRON_TRAPDOOR).save(h.getLevel().registryAccess()));tag.putString("OutputId","minecraft:iron_trapdoor");tome.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));((Container)h.getBlockEntity(shelf)).setItem(0,tome);
        var manager=NetworkManager.get(h.getLevel().getServer());var aa=((CrystalNodeBlockEntity)h.getBlockEntity(a)).address();var ba=((CrystalNodeBlockEntity)h.getBlockEntity(b)).address();var ca=((CrystalNodeBlockEntity)h.getBlockEntity(c)).address();
        h.assertTrue(manager.toggleLink(aa,ba).success()&&manager.toggleLink(ba,ca).success(),"The initial three-node graph is a connected tree");
        var ingredient=new ItemKey(new ItemStack(Items.IRON_INGOT));var machine=at(h,table);
        FakePlayer player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"rebuild-boundary"));player.setPos(h.absolutePos(a).getCenter());
        AstralNetwork[] service={null};UUID[] request={null};
        h.startSequence().thenWaitUntil(()->ready(h,manager.networkAt(aa),ingredient,machine,"Initial tree"))
                .thenExecute(()->{
                    service[0]=manager.networkAt(aa);request[0]=beginClaimedJob(h,service[0],player,machine);
                    var result=manager.toggleLink(aa,ca);h.assertTrue(result.success()&&result.linked(),"Adding the redundant edge creates a cycle without adding nodes");
                }).thenWaitUntil(()->{
                    var current=manager.networkAt(aa);h.assertTrue(current!=null&&current!=service[0]&&current==manager.networkAt(ba)&&current==manager.networkAt(ca),"An internal edge edit replaces the service while keeping all three addresses together");
                    cancelled(h,service[0],request[0]);ready(h,current,ingredient,machine,"After adding cycle edge");
                    h.assertTrue(count(inventory,Items.IRON_INGOT)==8&&count(inventory,Items.IRON_TRAPDOOR)==0,"Cancelling the claimed table operation refunds all eight iron ingots without producing a trapdoor");
                }).thenExecute(()->{
                    service[0]=manager.networkAt(aa);request[0]=beginClaimedJob(h,service[0],player,machine);
                    var result=manager.toggleLink(aa,ca);h.assertTrue(result.success()&&!result.linked(),"Removing the redundant edge leaves the original spanning tree intact");
                }).thenWaitUntil(()->{
                    var current=manager.networkAt(aa);h.assertTrue(current!=null&&current!=service[0]&&current==manager.networkAt(ba)&&current==manager.networkAt(ca),"Removing a cycle edge also refreshes the unchanged component membership");
                    cancelled(h,service[0],request[0]);ready(h,current,ingredient,machine,"After removing cycle edge");
                    h.assertTrue(count(inventory,Items.IRON_INGOT)==8&&count(inventory,Items.IRON_TRAPDOOR)==0&&!CraftingService.isProcessorReserved(machine),"The second cancellation refunds safely, produces nothing and releases the actual table");
                }).thenExecute(h::succeed);
    }
    private static void ready(GameTestHelper h,AstralNetwork network,ItemKey ingredient,GlobalPos table,String phase){
        h.assertTrue(network!=null,phase+": network has not registered");
        h.assertTrue(network.nodes().size()==3,phase+": expected three connected anchors, got "+network.nodes().stream().map(NetworkAnchor::address).toList());
        h.assertTrue(network.snapshot().getOrDefault(ingredient,0L)==8,phase+": expected eight indexed iron ingots, got "+network.snapshot()+"; "+network.status());
        h.assertTrue(network.workstations().contains(table),phase+": expected table "+table+", got workstations "+network.workstations());
        h.assertTrue(network.exposedProducts().stream().anyMatch(s->s.is(Items.IRON_TRAPDOOR)),phase+": expected taught trapdoor, got products "+network.exposedProducts());
    }
    private static UUID beginClaimedJob(GameTestHelper h,AstralNetwork network,FakePlayer player,GlobalPos table){
        var request=network.crafting().request(player,new ItemStack(Items.IRON_TRAPDOOR),1);h.assertTrue(request.accepted(),"Actual crafting request accepted: "+request.message());
        CraftingGameTests.awaitPlanning(network.crafting());network.crafting().tick();h.assertTrue(CraftingService.isProcessorReserved(table)&&network.crafting().activeProcessors()==1,"The actual table is claimed before editing the graph: "+network.crafting().statuses());return request.jobId();
    }
    private static void cancelled(GameTestHelper h,AstralNetwork original,UUID id){h.assertTrue(original.crafting().activeJobs()==0&&original.crafting().statuses().stream().anyMatch(job->job.id().equals(id)&&job.state().equals("CANCELLED")&&job.completed()==0&&job.total()==1),"The obsolete service must cancel its active one-operation job before producing output: "+original.crafting().statuses());}    private NetworkReuseBoundaryGameTests(){}
}