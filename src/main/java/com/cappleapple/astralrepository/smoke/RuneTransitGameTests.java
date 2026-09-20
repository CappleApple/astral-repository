package com.cappleapple.astralrepository.smoke;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.compat.CompatibilityRegistry;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import com.cappleapple.astralrepository.port.storage.IFluidHandler;

/** Explicit one-tick cadences must not bypass the separate resource flight delay. */


public final class RuneTransitGameTests {
    private static final int[] BATCH = {7, 113, 257, 61};

    public static void noninstantPushHoldsAllFourResourcesUntilTheirArrival(GameTestHelper h) {
        delayed(h, RuneLayer.Mode.PUSH);
    }

    public static void noninstantPullHoldsAllFourResourcesUntilTheirArrival(GameTestHelper h) {
        delayed(h, RuneLayer.Mode.PULL);
    }

    private static void delayed(GameTestHelper h, RuneLayer.Mode mode) {
        boolean automatic = AstralConfig.instantAutomaticLogistics.get();
        boolean player = AstralConfig.instantPlayerInteractions.get();
        Fixture fixture = null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);
            AstralConfig.instantPlayerInteractions.set(true);
            fixture = new Fixture(h, mode, 1);
            fixture.worker.tick();
            fixture.assertSource(0, "Due dispatch removes each batch from its actual source");
            fixture.assertDestination(0, "An explicit one-tick cadence cannot insert before the flight arrives");
            h.assertTrue(fixture.rune.transferredItems()==0&&fixture.rune.transferredFluid()==0,
                    "Rune counters do not count still-travelling resources as delivered");
            // Stopping dispatches must not strand or accelerate resources already owned by a flight.
            fixture.rune.setEnabled(false);
            for (int elapsed=1; elapsed<fixture.travelTicks; elapsed++) {
                fixture.worker.tick();
                fixture.assertDestination(0, "All four destinations remain empty before due tick "+elapsed);
            }
            fixture.worker.tick();
            fixture.assertSource(0, "Arrival cannot return or re-extract already departed batches");
            fixture.assertDestination(1, "Each resource arrives in full at its due tick");
            h.assertTrue(fixture.rune.transferredItems()==BATCH[0]&&fixture.rune.transferredFluid()==BATCH[1],
                    "Delivery counters record completed item and fluid amounts exactly once");
            for (int elapsed=0; elapsed<fixture.travelTicks+2; elapsed++) fixture.worker.tick();
            fixture.assertDestination(1, "Completed flights cannot replay after the rune is disabled");
        } finally {
            if (fixture!=null) fixture.rune.setEnabled(false);
            AstralConfig.instantAutomaticLogistics.set(automatic);
            AstralConfig.instantPlayerInteractions.set(player);
        }
        h.succeed();
    }

    public static void instantPushAndPullCommitAllFourResourcesOnDispatch(GameTestHelper h) {
        boolean automatic = AstralConfig.instantAutomaticLogistics.get();
        boolean player = AstralConfig.instantPlayerInteractions.get();
        try {
            AstralConfig.instantAutomaticLogistics.set(true);
            AstralConfig.instantPlayerInteractions.set(false);
            for (var mode : List.of(RuneLayer.Mode.PUSH, RuneLayer.Mode.PULL)) {
                var fixture = new Fixture(h, mode, 1);
                try {
                    fixture.worker.tick();
                    fixture.assertSource(0, "Instant dispatch extracts each real source batch");
                    fixture.assertDestination(1, "Instant automatic mode inserts every medium in the same tick");
                    h.assertTrue(fixture.rune.transferredItems()==BATCH[0]&&fixture.rune.transferredFluid()==BATCH[1],
                            "Instant mode counts only the exact committed transfer");
                    for (int i=0; i<fixture.travelTicks+2; i++) fixture.worker.tick();
                    fixture.assertDestination(1, "Instant dispatch does not also schedule a duplicate arrival");
                } finally { fixture.rune.setEnabled(false); }
            }
        } finally {
            AstralConfig.instantAutomaticLogistics.set(automatic);
            AstralConfig.instantPlayerInteractions.set(player);
        }
        h.succeed();
    }

    public static void oneTickCadencePipelinesThreeBatchesWithoutFinishingThemEarly(GameTestHelper h) {
        boolean automatic = AstralConfig.instantAutomaticLogistics.get();
        Fixture fixture = null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);
            fixture = new Fixture(h, RuneLayer.Mode.PUSH, 3);
            for (int departed=1; departed<=3; departed++) {
                fixture.worker.tick();
                fixture.assertSource(3-departed, "Cadence dispatches the next batch while earlier batches are still travelling");
                fixture.assertDestination(0, "A pipeline cannot deposit any of its three batches prematurely");
            }
            fixture.rune.setEnabled(false);
            // An effective config reload applies to new dispatches, not already scheduled arrivals.
            AstralConfig.instantAutomaticLogistics.set(true);
            for (int elapsed=3; elapsed<fixture.travelTicks; elapsed++) {
                fixture.worker.tick();
                fixture.assertDestination(0, "Reloading instant mode cannot complete an old flight early");
            }
            for (int arrived=1; arrived<=3; arrived++) {
                fixture.worker.tick();
                fixture.assertDestination(arrived, "Consecutive departures preserve their individual arrival ticks");
            }
            fixture.assertSource(0, "Pipeline conserves every departed batch");
            h.assertTrue(fixture.rune.transferredItems()==3L*BATCH[0]&&fixture.rune.transferredFluid()==3L*BATCH[1],
                    "Pipeline counters equal all three completed batches without duplication");
        } finally {
            if (fixture!=null) fixture.rune.setEnabled(false);
            AstralConfig.instantAutomaticLogistics.set(automatic);
        }
        h.succeed();
    }

    public static void stockTargetsCountAllInFlightReservationsBeforeSchedulingMore(GameTestHelper h) {
        boolean automatic=AstralConfig.instantAutomaticLogistics.get();
        Fixture fixture=null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);
            fixture=new Fixture(h,RuneLayer.Mode.PUSH,3);
            fixture.rune.filter().setTarget(17);fixture.rune.changed();
            for(int elapsed=0;elapsed<10;elapsed++)fixture.worker.tick();
            fixture.rune.setEnabled(false);
            fixture.assertDestination(0,"Stock reservations cannot make resources arrive early");
            var pending=RuneTransitData.get(h.getLevel().getServer());
            long[] remaining={fixture.source.inventory().used(),fixture.source.tank().getFluidAmount(),fixture.source.energy().getEnergyStored(),fixture.arsSource[0]};
            for(var kind:RuneCadence.Kind.values())h.assertTrue(pending.pendingAmount(fixture.rune.id(),key(kind))==17
                    &&remaining[kind.ordinal()]==3L*BATCH[kind.ordinal()]-17,"One-tick scheduling reserves at most the requested stock for "+kind);
            for(int elapsed=10;elapsed<=fixture.travelTicks+2;elapsed++)fixture.worker.tick();
            long[] arrived={fixture.destination.inventory().used(),fixture.destination.tank().getFluidAmount(),fixture.destination.energy().getEnergyStored(),fixture.arsSource[1]};
            for(var kind:RuneCadence.Kind.values())h.assertTrue(arrived[kind.ordinal()]==17&&pending.pendingAmount(fixture.rune.id(),key(kind))==0,
                    "Every medium reaches exactly its stock target after reserved flights arrive: "+kind);
        } finally {
            if(fixture!=null)fixture.rune.setEnabled(false);
            AstralConfig.instantAutomaticLogistics.set(automatic);
        }
        h.succeed();
    }

    public static void multipleRunesShareDestinationStockReservationsForEveryMedium(GameTestHelper h) {
        boolean automatic=AstralConfig.instantAutomaticLogistics.get();
        Fixture fixture=null;RuneLayer second=null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);
            fixture=new Fixture(h,RuneLayer.Mode.PUSH,6);
            var surface=fixture.rune.surface();
            fixture.rune.filter().setTarget(17);fixture.rune.changed();
            second=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
            second.setCadence(fixture.rune.cadence());second.filter().setTarget(17);second.changed();
            var destination=GlobalPos.of(h.getLevel().dimension(),fixture.destination.getBlockPos());
            h.assertTrue(surface.toggleTarget(second.id(),destination,Direction.SOUTH).success(),"Both runes bind the same physical four-medium destination");
            fixture.worker.track(surface);
            var pending=RuneTransitData.get(h.getLevel().getServer());
            for(int elapsed=0;elapsed<10;elapsed++){
                fixture.worker.tick();
                long[] source={fixture.source.inventory().used(),fixture.source.tank().getFluidAmount(),fixture.source.energy().getEnergyStored(),fixture.arsSource[0]};
                long[] arrived={fixture.destination.inventory().used(),fixture.destination.tank().getFluidAmount(),fixture.destination.energy().getEnergyStored(),fixture.arsSource[1]};
                for(var kind:RuneCadence.Kind.values()){
                    long owned=pending.pendingAmount(fixture.rune.id(),key(kind))+pending.pendingAmount(second.id(),key(kind));
                    h.assertTrue(arrived[kind.ordinal()]+owned<=17,"Competing runes cannot each reserve the whole destination stock target for "+kind);
                    h.assertTrue(source[kind.ordinal()]+arrived[kind.ordinal()]+owned==6L*BATCH[kind.ordinal()],"Both rune reservations conserve the original "+kind+" supply");
                }
            }
            fixture.rune.setEnabled(false);second.setEnabled(false);
            fixture.assertDestination(0,"Shared stock reservations retain the physical travel delay");
            for(var kind:RuneCadence.Kind.values())h.assertTrue(pending.pendingAmount(destination,key(kind))==17,"All runes share exactly one seventeen-unit destination reservation for "+kind);
            for(int elapsed=10;elapsed<=fixture.travelTicks+2;elapsed++)fixture.worker.tick();
            long[] arrived={fixture.destination.inventory().used(),fixture.destination.tank().getFluidAmount(),fixture.destination.energy().getEnergyStored(),fixture.arsSource[1]};
            for(var kind:RuneCadence.Kind.values())h.assertTrue(arrived[kind.ordinal()]==17&&pending.pendingAmount(destination,key(kind))==0,"Competing runes deliver exactly seventeen total units of "+kind);
            h.assertTrue(fixture.rune.transferredItems()+second.transferredItems()==17&&fixture.rune.transferredFluid()+second.transferredFluid()==17,"Combined rune counters count each shared stock delivery once");
        } finally {
            if(fixture!=null)fixture.rune.setEnabled(false);
            if(second!=null)second.setEnabled(false);
            AstralConfig.instantAutomaticLogistics.set(automatic);
        }
        h.succeed();
    }

    public static void fullDestinationRefundsAllFourOwnedBatchesAtArrival(GameTestHelper h) {
        boolean automatic=AstralConfig.instantAutomaticLogistics.get();
        Fixture fixture=null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);
            fixture=new Fixture(h,RuneLayer.Mode.PUSH,1);
            fixture.worker.tick();fixture.rune.setEnabled(false);
            fixture.assertSource(0,"All four outgoing batches have left the source");
            fixture.destination.inventory().insertItem(0,new ItemStack(Items.DIRT,Integer.MAX_VALUE),false);
            fixture.destination.tank().fill(new FluidStack(Fluids.LAVA,fixture.destination.tank().getTankCapacity(0)),IFluidHandler.FluidAction.EXECUTE);
            while(fixture.destination.energy().receiveEnergy(Integer.MAX_VALUE,false)>0) {}
            fixture.arsSource[1]=10000;
            h.assertTrue(fixture.destination.inventory().used()==fixture.destination.inventory().capacity(),"Fixture destination is physically full after dispatch");
            for(int elapsed=1;elapsed<fixture.travelTicks;elapsed++)fixture.worker.tick();
            fixture.assertSource(0,"Destination rejection cannot refund a batch before its scheduled arrival");
            fixture.worker.tick();
            fixture.assertSource(1,"A destination filled during travel returns every rejected medium to its original source");
            h.assertTrue(fixture.rune.transferredItems()==0&&fixture.rune.transferredFluid()==0,"Refunds cannot count as successful deliveries");
            fixture.assertPending(0,"Rejected deliveries no longer own duplicate in-flight resources");
            for(int elapsed=0;elapsed<fixture.travelTicks;elapsed++)fixture.worker.tick();
            fixture.assertSource(1,"Rejected deliveries refund exactly once");
        } finally {
            if(fixture!=null)fixture.rune.setEnabled(false);
            AstralConfig.instantAutomaticLogistics.set(automatic);
        }
        h.succeed();
    }

    public static void removedDestinationRefundsOwnedBatchesWithoutUsingStaleCapabilities(GameTestHelper h) {
        boolean automatic=AstralConfig.instantAutomaticLogistics.get();
        Fixture fixture=null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);
            fixture=new Fixture(h,RuneLayer.Mode.PUSH,1);
            fixture.worker.tick();fixture.rune.setEnabled(false);
            h.getLevel().setBlockAndUpdate(fixture.destination.getBlockPos(),Blocks.AIR.defaultBlockState());
            for(int elapsed=1;elapsed<=fixture.travelTicks;elapsed++)fixture.worker.tick();
            fixture.assertSource(1,"Removing the loaded destination refunds all four resources to their source");
            fixture.assertDestination(0,"An invalidated destination capability must never receive an arrival");
            fixture.assertPending(0,"Removed-target refunds clear in-flight ownership exactly once");
            for(int elapsed=0;elapsed<fixture.travelTicks;elapsed++)fixture.worker.tick();
            fixture.assertSource(1,"Removed-target refunds do not replay");
        } finally {
            if(fixture!=null)fixture.rune.setEnabled(false);
            AstralConfig.instantAutomaticLogistics.set(automatic);
        }
        h.succeed();
    }

    public static void savedFlightsPreserveEveryMediumAndRemainingTimeAcrossClockReset(GameTestHelper h) {
        var before=new RuneTransitData();
        var rune=UUID.randomUUID();
        var from=new RuneTransitData.Endpoint(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(new BlockPos(3,2,3))),Direction.SOUTH,"source",new Object());
        var to=new RuneTransitData.Endpoint(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(new BlockPos(6,2,3))),Direction.NORTH,"target",new Object());
        before.tick(1000,flight->{throw new AssertionError("Empty ledger delivered something");});
        for(var kind:RuneCadence.Kind.values())before.enqueue(1020,new RuneTransitData.Flight(rune,from,to,key(kind),BATCH[kind.ordinal()]));
        before.tick(1007,flight->{throw new AssertionError("Flight arrived before save");});
        var restored=RuneTransitData.load(before.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        var arrived=new java.util.ArrayList<RuneTransitData.Flight>();
        // Loading into a restarted server rebases the thirteen remaining ticks, independent of uptime.
        restored.tick(5,arrived::add);
        for(var kind:RuneCadence.Kind.values())h.assertTrue(restored.pendingAmount(rune,key(kind))==BATCH[kind.ordinal()]
                &&restored.pendingAmount(to.position(),key(kind))==BATCH[kind.ordinal()],"Saved ownership retains each medium and target index");
        restored.tick(17,arrived::add);
        h.assertTrue(arrived.isEmpty(),"Restarted flights remain owned until their remaining travel time elapses");
        restored.tick(18,arrived::add);
        h.assertTrue(arrived.size()==4,"All four saved media arrive exactly at the rebased deadline");
        for(var flight:arrived){
            h.assertTrue(flight.rune().equals(rune)&&flight.from().position().equals(from.position())&&flight.to().position().equals(to.position())
                    &&flight.from().side()==Direction.SOUTH&&flight.to().side()==Direction.NORTH,"Save keeps rune identity, endpoint positions and actual source/destination faces");
            h.assertTrue(flight.from().live()==null&&flight.to().live()==null,"Reload never retains stale capability references");
            h.assertTrue(restored.pendingAmount(rune,flight.resource())==0&&restored.pendingAmount(to.position(),flight.resource())==0,"Delivery releases both reservation indexes");
        }
        restored.tick(1000,arrived::add);
        h.assertTrue(arrived.size()==4,"Rebased saved flights complete only once");
        h.succeed();
    }

    public static void uncertainArrivalIsRecordedWithoutRefundOrReplay(GameTestHelper h) {
        boolean automatic=AstralConfig.instantAutomaticLogistics.get();
        RuneLayer rune=null;
        try {
            AstralConfig.instantAutomaticLogistics.set(false);
            BlockPos from=new BlockPos(3,2,3),to=new BlockPos(6,2,3);
            h.setBlock(from,Blocks.CHEST);h.setBlock(to,Blocks.ENCHANTING_TABLE);
            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getBlockEntity(from);
            chest.setItem(0,new ItemStack(Items.IRON_INGOT,64));
            int[] accepted={0};var iron=new ItemKey(new ItemStack(Items.IRON_INGOT));
            String id="transit_uncertain_"+UUID.randomUUID();
            StorageProvider broken=new StorageProvider() {
                public String id(){return id;}public Object identity(){return this;}
                public boolean valid(){return h.getLevel().getBlockState(h.absolutePos(to)).is(Blocks.ENCHANTING_TABLE);}
                public long capacity(){return 64;}
                public java.util.Map<ItemKey,Long> snapshot(){return java.util.Map.of(iron,(long)accepted[0]);}
                public ItemStack extract(ItemKey key,int amount,boolean simulate){return ItemStack.EMPTY;}
                public ItemStack insert(ItemStack stack,boolean simulate){
                    if(simulate)return ItemStack.EMPTY;
                    accepted[0]+=stack.getCount();throw new IllegalStateException("Expected delayed fixture failure after commit");
                }
            };
            CompatibilityRegistry.registerStorage(id,(level,pos,side)->level==h.getLevel()&&pos.equals(h.absolutePos(to))?List.of(broken):List.of());
            var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(from),Direction.SOUTH);
            rune=surface.addLayer(RuneGlyph.id(RuneLayer.Mode.PUSH),RuneLayer.Mode.PUSH);
            RuneCadence cadence=RuneCadence.DEFAULT;
            for(var kind:RuneCadence.Kind.values())cadence=cadence.with(kind,new RuneCadence.Rate(kind==RuneCadence.Kind.ITEMS?7:0,100));
            rune.setCadence(cadence);
            h.assertTrue(surface.toggleTarget(rune.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(to)),Direction.WEST).success(),"Indeterminate destination accepts a real sided binding");
            var worker=new DirectRuneTransfers(h.getLevel().getServer());worker.track(surface);worker.tick();
            int duration=TransferVisuals.duration(List.of(h.absolutePos(from),h.absolutePos(to)));
            for(int elapsed=1;elapsed<duration;elapsed++)worker.tick();
            h.assertTrue(accepted[0]==0&&chest.getItem(0).getCount()==57,"An uncertain third-party handler is untouched until arrival");
            worker.tick();
            h.assertTrue(accepted[0]==7&&chest.getItem(0).getCount()==57&&!rune.enabled(),"A throw after destination mutation pauses the rune without fabricating a refund");
            for(int elapsed=0;elapsed<duration+2;elapsed++)worker.tick();
            h.assertTrue(accepted[0]==7&&chest.getItem(0).getCount()==57,"Indeterminate arrival is not replayed");
            var entries=TransferRecoveryData.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getListOrEmpty("Transfers");
            boolean found=false;
            for(int i=0;i<entries.size();i++){var entry=entries.getCompoundOrEmpty(i);if(entry.getStringOr("Provider","").contains(id)&&entry.getBooleanOr("Uncertain",false)&&entry.getLongOr("Amount",0L)==7)found=true;}
            h.assertTrue(found&&RuneTransitData.get(h.getLevel().getServer()).pendingAmount(rune.id(),iron)==0,"Uncertain resources leave the live queue and remain in the non-replaying recovery journal");
        } finally {
            if(rune!=null)rune.setEnabled(false);
            AstralConfig.instantAutomaticLogistics.set(automatic);
        }
        h.succeed();
    }

    private static ResourceKey key(RuneCadence.Kind kind) {
        return switch(kind) {
            case ITEMS -> new ItemKey(new ItemStack(Items.IRON_INGOT));
            case FLUID -> new FluidKey(new FluidStack(Fluids.WATER,1));
            case ENERGY -> ResourceKinds.FE;
            case SOURCE -> ResourceKinds.ARS_SOURCE;
        };
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final CrystalNodeBlockEntity source, destination;
        final long[] arsSource;
        final RuneLayer rune;
        final DirectRuneTransfers worker;
        final int travelTicks;

        Fixture(GameTestHelper h, RuneLayer.Mode mode, int batches) {
            helper=h;
            int z=mode==RuneLayer.Mode.PUSH?3:7;
            BlockPos hostPos=new BlockPos(3,2,z),targetPos=new BlockPos(6,2,z);
            h.setBlock(hostPos,AstralContent.SEED_STORAGE_CRYSTAL.get());
            h.setBlock(targetPos,AstralContent.SEED_STORAGE_CRYSTAL.get());
            var host=(CrystalNodeBlockEntity)h.getBlockEntity(hostPos);
            var target=(CrystalNodeBlockEntity)h.getBlockEntity(targetPos);
            source=mode==RuneLayer.Mode.PUSH?host:target;
            destination=mode==RuneLayer.Mode.PUSH?target:host;
            source.inventory().insertItem(0,new ItemStack(Items.IRON_INGOT,batches*BATCH[0]),false);
            source.tank().fill(new FluidStack(Fluids.WATER,batches*BATCH[1]),IFluidHandler.FluidAction.EXECUTE);
            source.energy().receiveEnergy(batches*BATCH[2],false);
            arsSource=new long[]{batches*BATCH[3],0};
            String id="rune_transit_"+UUID.randomUUID();
            CompatibilityRegistry.registerResources(id,(level,pos,side)->{
                if(level!=h.getLevel())return List.of();
                int index=pos.equals(source.getBlockPos())?0:pos.equals(destination.getBlockPos())?1:-1;
                if(index<0)return List.of();
                var owner=index==0?source:destination;
                if(level.getBlockEntity(pos)!=owner)return List.of();
                var delegate=RuneTransitSmoke.scalar(id+index,arsSource,index);
                return List.of(new ResourceProvider(){
                    public String id(){return delegate.id();}public Object identity(){return delegate.identity();}
                    public boolean valid(){return !owner.isRemoved()&&level.getBlockEntity(pos)==owner;}
                    public long capacity(){return delegate.capacity();}
                    public net.minecraft.resources.Identifier resourceType(){return delegate.resourceType();}
                    public java.util.Map<ResourceKey,Long> snapshot(){return delegate.snapshot();}
                    public long insert(ResourceKey key,long amount,boolean simulate){return delegate.insert(key,amount,simulate);}
                    public long extract(ResourceKey key,long amount,boolean simulate){return delegate.extract(key,amount,simulate);}
                });
            });
            var surface=RuneSurfaces.getOrCreate(h.getLevel(),h.absolutePos(hostPos),Direction.SOUTH);
            rune=surface.addLayer(RuneGlyph.id(mode),mode);
            RuneCadence cadence=RuneCadence.DEFAULT;
            for(var kind:RuneCadence.Kind.values())cadence=cadence.with(kind,new RuneCadence.Rate(BATCH[kind.ordinal()],1));
            rune.setCadence(cadence);
            h.assertTrue(surface.toggleTarget(rune.id(),GlobalPos.of(h.getLevel().dimension(),h.absolutePos(targetPos)),Direction.SOUTH).success(),
                    "Each mode binds its real four-medium storage target");
            worker=new DirectRuneTransfers(h.getLevel().getServer());worker.track(surface);
            travelTicks=TransferVisuals.duration(List.of(source.getBlockPos(),destination.getBlockPos()));
            h.assertTrue(travelTicks>3,"Fixture keeps several dispatches simultaneously in flight");
        }

        void assertPending(int batches,String message) {
            var transit=RuneTransitData.get(helper.getLevel().getServer());
            for(var kind:RuneCadence.Kind.values())helper.assertTrue(transit.pendingAmount(rune.id(),key(kind))==(long)batches*BATCH[kind.ordinal()],message+" ("+kind+")");
        }
        void assertSource(int batches,String message) { assertAmounts(source,arsSource[0],batches,message); }
        void assertDestination(int batches,String message) { assertAmounts(destination,arsSource[1],batches,message); }
        private void assertAmounts(CrystalNodeBlockEntity storage,long sourceAmount,int batches,String message) {
            long[] actual={storage.inventory().used(),storage.tank().getFluidAmount(),storage.energy().getEnergyStored(),sourceAmount};
            for(var kind:RuneCadence.Kind.values())helper.assertTrue(actual[kind.ordinal()]==(long)batches*BATCH[kind.ordinal()],
                    message+" ("+kind+": expected "+(long)batches*BATCH[kind.ordinal()]+", got "+actual[kind.ordinal()]+")");
        }
    }
    private RuneTransitGameTests() {}
}
