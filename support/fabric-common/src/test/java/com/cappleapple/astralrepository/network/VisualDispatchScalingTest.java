package com.cappleapple.astralrepository.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualDispatchScalingTest {
    @Test void tenThousandOffersHaveBoundedStorageAndDoNotFavorTheFirstTransfers() {
        var sample = new VisualSampler<Integer>(128, 519L);
        var copies = new AtomicInteger();
        for (int i = 0; i < 10000; i++) {
            int value = i;
            sample.offer(() -> { copies.incrementAndGet(); return value; });
            assertTrue(sample.entries().size() <= 128);
        }
        assertEquals(10000, sample.seen()); assertEquals(128, sample.entries().size());
        assertEquals(128, new HashSet<>(sample.entries()).size());
        assertTrue(copies.get() < 1000, "Rejected cosmetics must not copy stacks or encode components");
        long first = sample.entries().stream().filter(i -> i < 2500).count();
        long last = sample.entries().stream().filter(i -> i >= 7500).count();
        assertTrue(first > 10 && last > 10, "Transfers across the whole tick remain represented");
    }

    @Test void perPlayerBudgetsKeepIndependentDistantNetworksVisible() {
        var busy = new VisualSampler<Integer>(128, 1); var quiet = new VisualSampler<Integer>(128, 2);
        for (int i = 0; i < 10000; i++) { int value = i; busy.offer(() -> value); }
        quiet.offer(() -> 10000);
        assertEquals(List.of(10000), quiet.entries()); assertEquals(128, busy.entries().size());
    }

    @Test void audienceIncludesObserversAlongLongLegsAndExcludesVerticalAndDistantObservers() {
        var index = new VisualAudienceIndex<>(List.of(
                new VisualAudienceIndex.Observer<>("middle", new Vec3(500, 4, 0)),
                new VisualAudienceIndex.Observer<>("above", new Vec3(500, 250, 0)),
                new VisualAudienceIndex.Observer<>("far", new Vec3(500, 0, 500)),
                new VisualAudienceIndex.Observer<>("source", new Vec3(-20, 0, 0))));
        assertEquals(List.of("middle", "source"), index.nearby(List.of(BlockPos.ZERO, new BlockPos(1000, 0, 0))));
        assertTrue(index.nearby(List.of(new BlockPos(10000, 0, 10000), new BlockPos(10002, 0, 10000))).isEmpty());
    }

    @Test void spatialQueriesHandleWorldBorderSpanningRoutesAndNegativeCells() {
        var index = new VisualAudienceIndex<>(List.of(new VisualAudienceIndex.Observer<>("negative", new Vec3(-129, 0, -129))));
        assertEquals(List.of("negative"), index.nearby(List.of(new BlockPos(-30000000, 0, -30000000), new BlockPos(30000000, 0, 30000000))));
        assertEquals(List.of("negative"), index.nearby(List.of(new BlockPos(-140, 0, -130), new BlockPos(-128, 0, -130))));
        assertTrue(new VisualAudienceIndex<String>(List.of()).nearby(List.of(BlockPos.ZERO, new BlockPos(1, 0, 0))).isEmpty());
    }

    @Test void expandedBoundsPreserveBoundaryAndIntermediateLegObservers() {
        var route=List.of(BlockPos.ZERO,new BlockPos(1000,0,0),new BlockPos(1000,500,1000));
        var index=new VisualAudienceIndex<>(List.of(
                new VisualAudienceIndex.Observer<>("near middle",new Vec3(500.5,132.4999,.5)),
                new VisualAudienceIndex.Observer<>("exact radius",new Vec3(500.5,132.5,.5)),
                new VisualAudienceIndex.Observer<>("outside radius",new Vec3(500.5,132.5001,.5)),
                new VisualAudienceIndex.Observer<>("diagonal middle",new Vec3(1000.5,250.5,500.5)),
                new VisualAudienceIndex.Observer<>("beyond vertical bounds",new Vec3(1000.5,1000.5,1000.5))));
        assertEquals(List.of("near middle","diagonal middle"),index.nearby(route));
        assertTrue(index.nearby(List.of(new BlockPos(-30000,-30000,-30000),new BlockPos(-29999,-30000,-30000))).isEmpty());
    }

    @Test void boundingRejectionMatchesExactSegmentChecksForScatteredObserversAndRoutes() {
        var random=new java.util.Random(173011);var observers=new ArrayList<VisualAudienceIndex.Observer<Integer>>();
        for(int i=0;i<128;i++)observers.add(new VisualAudienceIndex.Observer<>(i,
                new Vec3(random.nextInt(8192)-4096,random.nextInt(1024)-512,random.nextInt(8192)-4096)));
        var index=new VisualAudienceIndex<>(observers);
        for(int trial=0;trial<200;trial++){
            var path=new ArrayList<BlockPos>();
            for(int point=0;point<2+trial%7;point++)path.add(new BlockPos(random.nextInt(8192)-4096,random.nextInt(1024)-512,random.nextInt(8192)-4096));
            var expected=new HashSet<Integer>();
            for(var observer:observers)if(VisualAudienceIndex.nearRoute(observer.position(),path))expected.add(observer.value());
            assertEquals(expected,new HashSet<>(index.nearby(path)),"Bounding optimization must not drop or add an observer on trial "+trial);
        }
    }

    @Test void batchLimitsRejectTooManyEntriesAndOversizedEncodedIcons() {
        assertThrows(IllegalArgumentException.class, () -> TransferVisualBatch.encoded(false,
                java.util.Collections.nCopies(TransferVisualBatch.MAX_ENTRIES + 1, new byte[1])));
        assertThrows(IllegalArgumentException.class, () -> TransferVisualBatch.encoded(false, List.of(new byte[TransferVisualBatch.MAX_BYTES])));
        assertDoesNotThrow(() -> TransferVisualBatch.encoded(true, List.of()));
    }

    @Test void tenThousandOffersAcrossFourAudiencesStayBounded() {
        var observers = new ArrayList<VisualAudienceIndex.Observer<Integer>>();
        for (int i = 0; i < 1000; i++) observers.add(new VisualAudienceIndex.Observer<>(i, new Vec3(i * 1024.0, 0, 0)));
        var index = new VisualAudienceIndex<>(observers);
        var queues = List.of(new VisualSampler<Integer>(128, 10), new VisualSampler<Integer>(128, 20),
                new VisualSampler<Integer>(128, 30), new VisualSampler<Integer>(128, 40));
        int copies = 0; long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            int observer = i % 4, value = i;
            var route = List.of(new BlockPos(observer * 1024, 0, i % 64), new BlockPos(observer * 1024 + 8, 0, i % 64));
            for (int recipient : index.nearby(route)) if (queues.get(recipient).offer(() -> value)) copies++;
        }
        double millis = (System.nanoTime() - start) / 1000000.0;
        for (var queue : queues) { assertEquals(2500, queue.seen()); assertEquals(128, queue.entries().size()); }
        assertTrue(copies < 2400);
        System.out.printf("Visual dispatch core: 10000 offers, 1000 indexed observers, 4 active audiences, 512 retained, %d accepted allocations, %.3f ms%n", copies, millis);
    }
}
