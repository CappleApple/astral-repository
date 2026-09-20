package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;
import com.cappleapple.astralrepository.network.TransferVisuals;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PreparedTransferPathTest {
    private static volatile Vec3 sampled;

    @Test void matchesExistingSplineAndVariationAcrossRouteSizesAndWorldBorderCoordinates() {
        for (int count : new int[]{2, 3, 9, 32, 130}) {
            for (int origin : new int[]{0, 29_999_000, -29_999_000}) {
                List<BlockPos> path = path(count, origin);
                var packet = packet(path, -1, null, null);
                for (double variation : new double[]{0, .2, 2}) {
                    long seed = 3_791_649_172L;
                    var prepared = PreparedTransferPath.create(packet, seed, variation);
                    for (int i = -1; i <= 301; i++)
                        assertSamePoint(TransferVisuals.variedPosition(packet, i / 300.0, seed, variation), prepared.position(i / 300.0));
                    int elapsed = 0;
                    for (int i = 1; i < path.size(); i++) {
                        int legTicks = TransferVisuals.legTicks(path.get(i - 1), path.get(i));
                        double middle = (elapsed + legTicks * .5) / packet.duration();
                        elapsed += legTicks;
                        double end = (double) elapsed / packet.duration();
                        // Exercise both sides of the binary-search and random-knot boundaries.
                        for (double t : new double[]{Math.nextDown(middle), middle, Math.nextUp(middle), Math.nextDown(end), end, Math.nextUp(end)})
                            assertSamePoint(TransferVisuals.variedPosition(packet, t, seed, variation), prepared.position(t));
                    }
                }
            }
        }
    }

    @Test void preservesAllRuneNormalsRepeatedNodesAndEachResourceArrival() {
        List<BlockPos> path = List.of(BlockPos.ZERO, BlockPos.ZERO, new BlockPos(0, 24, 0), new BlockPos(24, 25, 0));
        for (Direction face : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
            var endpoint = new TransferVisuals.Endpoint(normal.scale(.502), face);
            for (int medium = -4; medium <= -1; medium++) {
                var packet = packet(path, medium, endpoint, endpoint);
                var arrival = ResourceArrival.create(packet, 719, .45);
                var prepared = PreparedTransferPath.create(packet, 719, 2, arrival);
                for (int i = 0; i <= 500; i++) {
                    double t = i / 500.0;
                    Vec3 expected = TransferVisuals.variedPosition(packet, t, 719, 2);
                    if (arrival != null) expected = arrival.position(expected, t);
                    assertSamePoint(expected, prepared.position(t));
                }
                double h = 1e-6;
                Vec3 outgoing = prepared.position(h).subtract(prepared.position(0)).scale(1 / h);
                Vec3 incoming = prepared.position(1).subtract(prepared.position(1 - h)).scale(1 / h);
                assertTrue(outgoing.normalize().dot(normal) > .999, "Departure follows " + face);
                assertTrue(incoming.normalize().dot(normal) < -.999, "Arrival opposes " + face);
                if (arrival != null) assertSamePoint(arrival.endpoint(), prepared.position(1));
            }
        }
    }

    @Test void relayOffsetsRetainSpreadAndContinuousVelocity() {
        List<BlockPos> path = List.of(BlockPos.ZERO, new BlockPos(8, 0, 0), new BlockPos(8, 0, 16));
        var packet = packet(path, -1, null, null);
        double h = 1e-6, relay = 1.0 / 3;
        double relayEnergy = 0, midEnergy = 0;
        for (long seed = 0; seed < 200; seed++) {
            var prepared = PreparedTransferPath.create(packet, seed, .2);
            Vec3 at = prepared.position(relay);
            Vec3 before = at.subtract(prepared.position(relay - h)).scale(1 / h);
            Vec3 after = prepared.position(relay + h).subtract(at).scale(1 / h);
            assertTrue(before.distanceTo(after) < .005, "A prepared route keeps the relay tangent continuous");
            relayEnergy += at.distanceToSqr(TransferVisuals.position(packet, relay));
            midEnergy += prepared.position(relay * .5).distanceToSqr(TransferVisuals.position(packet, relay * .5));
        }
        assertTrue(relayEnergy / midEnergy > .8 && relayEnergy / midEnergy < 1.2, "Precomputation must not tighten paths at relays");
    }

    @Test void highVolumeSamplingAllocatesOnlyReturnedPositions() {
        var bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocations = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        if (!allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        var packet = packet(path(130, 0), -2, null, null);
        var prepared = PreparedTransferPath.create(packet, 591, .2, ResourceArrival.create(packet, 591, .18));
        for (int i = 0; i < 100_000; i++) sampled = prepared.position((i % 10_000) / 10_000.0);
        long thread = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 100_000; i++) sampled = prepared.position((i % 10_000) / 10_000.0);
        long bytes = allocations.getThreadAllocatedBytes(thread) - before;
        // Vec3 occupies 40 bytes with the supported HotSpot layout; allow header differences.
        assertTrue(bytes <= 4_800_000, "Sampling allocated " + bytes + " bytes; route construction must remain outside the frame loop");
        assertTrue(Double.isFinite(sampled.lengthSqr()));
    }

    private static void assertSamePoint(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1e-7);
        assertEquals(expected.y, actual.y, 1e-7);
        assertEquals(expected.z, actual.z, 1e-7);
    }

    private static List<BlockPos> path(int count, int origin) {
        var result = new ArrayList<BlockPos>();
        var random = new Random(839_497);
        BlockPos point = new BlockPos(origin, 32, origin);
        for (int i = 0; i < count; i++) {
            result.add(point);
            if (i % 7 != 0) point = point.offset(random.nextInt(81) - 40, random.nextInt(17) - 8, random.nextInt(81) - 40);
        }
        return result;
    }

    private static NetworkPackets.Visual packet(List<BlockPos> path, int slot, TransferVisuals.Endpoint departure, TransferVisuals.Endpoint arrival) {
        return new NetworkPackets.Visual(path.get(0), path.get(path.size()-1), ItemStack.EMPTY, 0xffffff,
                TransferVisuals.duration(path), slot, path, departure, arrival);
    }
}
