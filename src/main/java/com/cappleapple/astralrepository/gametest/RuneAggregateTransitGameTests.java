package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.AstralServerConfig;
import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.api.ResourceKinds;
import com.cappleapple.astralrepository.api.StorageProvider;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class RuneAggregateTransitGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="aggregate_rune_transit")
    public static void networkRunesUseActualStorageTravelTimesAndKeepPolicyAcrossRebuilds(GameTestHelper h)throws Exception {
        boolean instant=AstralConfig.instantAutomaticLogistics.get();
        double range=AstralServerConfig.wandBindingRange.get();
        try {
            AstralConfig.instantAutomaticLogistics.set(false);AstralServerConfig.wandBindingRange.set(4.0);
            for(var mode:List.of(RuneLayer.Mode.PUSH,RuneLayer.Mode.PULL)) {
                int z=mode==RuneLayer.Mode.PUSH?3:67;
                BlockPos hostPos=new BlockPos(1,2,z),relayPos=new BlockPos(4,2,z),nearPos=new BlockPos(18,2,z),farPos=new BlockPos(32,2,z);
                for(var pos:List.of(hostPos,relayPos,nearPos,farPos))h.getLevel().getChunkAt(h.absolutePos(pos));
                h.setBlock(hostPos,Blocks.CHEST);h.setBlock(relayPos,AstralContent.RELAY_CRYSTAL.get());
                h.setBlock(nearPos,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(farPos,AstralContent.SEED_STORAGE_CRYSTAL.get());
                var host=(Container)h.getBlockEntity(hostPos);
                var relay=(CrystalNodeBlockEntity)h.getBlockEntity(relayPos);
                var near=(CrystalNodeBlockEntity)h.getBlockEntity(nearPos);
                var far=(CrystalNodeBlockEntity)h.getBlockEntity(farPos);
                int channel=mode==RuneLayer.Mode.PUSH?10:11;
                for(var node:List.of(relay,near,far))node.setChannel(channel);
                for(var node:List.of(near,far))node.insertionFilter().setTarget(16);
                var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.SOUTH);surface.setChannel(channel);
                var rune=surface.addLayer(RuneGlyph.id(mode),mode);rune.setEnabled(false);
                rune.setCadence(RuneCadence.DEFAULT.with(RuneCadence.Kind.ITEMS,new RuneCadence.Rate(32,1)));
                rune.filter().add(FilterRules.Kind.ITEM,"minecraft:iron_ingot",false,ItemStack.EMPTY);
                if(mode==RuneLayer.Mode.PUSH)host.setItem(0,new ItemStack(Items.IRON_INGOT,32));
                else for(var node:List.of(near,far))node.inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,16),false);
                h.assertTrue(surface.toggleTarget(rune.id(),at(h,relayPos),null).success(),"Rune binds to the aggregate Relay network");
                var manager=NetworkManager.get(h.getLevel().getServer());
                var managerTick=NetworkManager.class.getDeclaredMethod("tick");managerTick.setAccessible(true);managerTick.invoke(manager);
                var network=manager.networkAt(relay.address());
                h.assertTrue(network!=null&&manager.networkAt(near.address())==network&&manager.networkAt(far.address())==network,"Fixture has one connected storage component");
                for(var pos:List.of(hostPos,nearPos,farPos))network.invalidate(at(h,pos));network.tick(32,32);
                var iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
                h.assertTrue(network.snapshot().getOrDefault(iron,0L)==32,"All physical fixture contents are indexed before dispatch");
                int relayTicks=travel(manager,at(h,hostPos),at(h,relayPos),channel);
                int nearTicks=travel(manager,at(h,hostPos),at(h,nearPos),channel);
                int farTicks=travel(manager,at(h,hostPos),at(h,farPos),channel);
                h.assertTrue(relayTicks<nearTicks&&nearTicks<farTicks,"Physical storage paths have distinct longer travel times than the selected Relay");
                var worker=new DirectRuneTransfers(h.getLevel().getServer());worker.track(surface);
                // Other synchronous fixtures advance the shared manager without advancing server ticks.
                // Both workers drive the same saved ledger, so start this worker on that existing clock.
                var managerWorker=NetworkManager.class.getDeclaredField("runeTransfers");managerWorker.setAccessible(true);
                var workerClock=DirectRuneTransfers.class.getDeclaredField("clock");workerClock.setAccessible(true);
                workerClock.setLong(worker,workerClock.getLong(managerWorker.get(manager)));
                rune.setEnabled(true);worker.tick();rune.setEnabled(false);long departed=workerClock.getLong(worker);
                h.assertTrue(count(host)+near.inventory().used()+far.inventory().used()==0,"Both actual batches leave their sources and remain in transit");
                h.assertTrue(RuneTransitData.get(h.getLevel().getServer()).pendingAmount(rune.id(),iron)==32,"Ledger owns both physical shipments");
                var restored=RuneTransitData.load(RuneTransitData.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
                var saved=new ArrayList<RuneTransitData.Flight>();restored.tick(0,saved::add);restored.tick(farTicks,saved::add);
                var ours=saved.stream().filter(f->f.rune().equals(rune.id())).toList();
                h.assertTrue(ours.size()==2,"Save retains separate physical endpoint shipments");
                for(var flight:ours){
                    var policy=mode==RuneLayer.Mode.PUSH?flight.to():flight.from();
                    h.assertTrue(policy.policyAnchor()!=null&&policy.live()==null,"Saved aggregate endpoints retain policy anchors without live capability references");
                    h.assertTrue(policy.position().equals(at(h,nearPos))||policy.position().equals(at(h,farPos)),"Saved route endpoint is physical storage, never the bound Relay");
                }
                if(mode==RuneLayer.Mode.PUSH){
                    var resolve=DirectRuneTransfers.class.getDeclaredMethod("resolve",RuneTransitData.Endpoint.class,com.cappleapple.astralrepository.api.ResourceKey.class);resolve.setAccessible(true);
                    var selected=ours.stream().filter(f->f.to().position().equals(at(h,nearPos))).findFirst().orElseThrow();
                    near.insertionFilter().add(FilterRules.Kind.ITEM,"minecraft:gold_ingot",false,ItemStack.EMPTY);
                    var target=(StorageProvider)resolve.invoke(worker,selected.to(),iron);
                    h.assertTrue(target instanceof RuneRouting.Policy&&target.insert(new ItemStack(Items.IRON_INGOT,16),true).getCount()==16,"Restored endpoint re-applies live network insertion policy");
                    near.insertionFilter().clear();near.insertionFilter().setTarget(16);
                }
                h.assertTrue(count(host)+near.inventory().used()+far.inventory().used()==0,"Policy simulation and serialization cannot mutate physical inventories");
                near.setPriority(1);managerTick.invoke(manager);
                h.assertTrue(manager.networkAt(relay.address())!=network,"Fixture replaced its aggregate component while resources were travelling");
                h.assertTrue(workerClock.getLong(managerWorker.get(manager))==departed,"Topology tick remains at this fixture's departure clock");
                for(int elapsed=1;elapsed<=farTicks;elapsed++){
                    worker.tick();
                    long expectedNear=elapsed>=nearTicks?16:0,expectedFar=elapsed>=farTicks?16:0;
                    if(mode==RuneLayer.Mode.PUSH)h.assertTrue(near.inventory().used()==expectedNear&&far.inventory().used()==expectedFar,"Network Push arrivals at elapsed="+elapsed+", near="+near.inventory().used()+" expected="+expectedNear+" due="+nearTicks+", far="+far.inventory().used()+" expected="+expectedFar+" due="+farTicks+", worker="+workerClock.getLong(worker)+", departed="+departed+", manager="+workerClock.getLong(managerWorker.get(manager)));
                    else h.assertTrue(count(host)==expectedNear+expectedFar,"Network Pull arrivals at elapsed="+elapsed+", actual="+count(host)+", expected="+(expectedNear+expectedFar)+", nearDue="+nearTicks+", farDue="+farTicks+", worker="+workerClock.getLong(worker)+", departed="+departed);
                    long physical=count(host)+near.inventory().used()+far.inventory().used();
                    h.assertTrue(physical+RuneTransitData.get(h.getLevel().getServer()).pendingAmount(rune.id(),iron)==32,"Rebuilding the network never loses or duplicates an owned shipment");
                }
                h.assertTrue(rune.transferredItems()==32,"Both completed physical shipments count exactly once");
                for(var pos:List.of(hostPos,relayPos,nearPos,farPos))h.setBlock(pos,Blocks.AIR);
            }
        }finally{AstralConfig.instantAutomaticLogistics.set(instant);AstralServerConfig.wandBindingRange.set(range);}
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository",template="empty_workshop",batch="aggregate_rune_transit")
    public static void perCallEnergyAcceptanceDoesNotSerializeOneTickFlights(GameTestHelper h){
        boolean instant=AstralConfig.instantAutomaticLogistics.get();RuneLayer rune=null;
        try{
            AstralConfig.instantAutomaticLogistics.set(false);
            var from=new BlockPos(3,2,3);var to=new BlockPos(6,2,3);
            h.setBlock(from,AstralContent.SEED_STORAGE_CRYSTAL.get());h.setBlock(to,AstralContent.SEED_STORAGE_CRYSTAL.get());
            var source=(CrystalNodeBlockEntity)h.getBlockEntity(from);var target=(CrystalNodeBlockEntity)h.getBlockEntity(to);
            for(int i=0;i<3;i++)h.assertTrue(source.energy().receiveEnergy(10000,false)==10000,"Fixture supplies three native FE batches");
            var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(from),Direction.SOUTH);
            rune=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
            rune.setCadence(RuneCadence.DEFAULT.with(RuneCadence.Kind.ENERGY,new RuneCadence.Rate(10000,1)));
            h.assertTrue(surface.toggleTarget(rune.id(),at(h,to),Direction.NORTH).success(),"Energy fixture binds direct physical sides");
            var worker=new DirectRuneTransfers(h.getLevel().getServer());worker.track(surface);
            for(int departed=1;departed<=3;departed++){
                worker.tick();
                h.assertTrue(source.energy().getEnergyStored()==30000-10000*departed,"A per-call 10000 FE receive limit still allows one batch per cadence tick");
                h.assertTrue(target.energy().getEnergyStored()==0,"Pipelined FE remains in transit before arrival");
            }
            rune.setEnabled(false);int duration=TransferVisuals.legTicks(h.absolutePos(from),h.absolutePos(to));
            for(int elapsed=3;elapsed<duration;elapsed++){worker.tick();h.assertTrue(target.energy().getEnergyStored()==0,"FE batches cannot arrive early");}
            for(int arrived=1;arrived<=3;arrived++){worker.tick();h.assertTrue(target.energy().getEnergyStored()==10000*arrived,"Consecutive FE departures arrive on consecutive ticks");}
            h.assertTrue(RuneTransitData.get(h.getLevel().getServer()).pendingAmount(rune.id(),ResourceKinds.FE)==0,"Completed pipeline releases its ownership exactly once");
        }finally{if(rune!=null)rune.setEnabled(false);AstralConfig.instantAutomaticLogistics.set(instant);}
        h.succeed();
    }
    private static GlobalPos at(GameTestHelper h,BlockPos pos){return GlobalPos.of(h.getLevel().dimension(),h.absolutePos(pos));}
    private static int travel(NetworkManager manager,GlobalPos from,GlobalPos to,int channel){return TransferVisuals.duration(manager.route(from,to,channel,4).stream().map(GlobalPos::pos).toList());}
    private static long count(Container container){long count=0;for(int i=0;i<container.getContainerSize();i++)if(container.getItem(i).is(Items.IRON_INGOT))count+=container.getItem(i).getCount();return count;}
    private RuneAggregateTransitGameTests(){}
}
