package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.network.NetworkPackets;
import com.cappleapple.astralrepository.network.TransferVisuals;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable geometry for one cosmetic flight. Construction resolves the route once;
 * sampling performs a binary leg lookup and allocates only its returned position.
 */
final class PreparedTransferPath {
    // Each node stores its position and velocity, in blocks per tick.
    private static final int NODE_STRIDE = 6;
    private final double[] nodes;
    private final int[] legEnds;
    private final double[] variationKnots;
    private final int duration;
    private final double arrivalStart;
    private final double arrivalX;
    private final double arrivalY;
    private final double arrivalZ;

    static PreparedTransferPath create(NetworkPackets.Visual packet, long seed, double variation) {
        return create(packet, seed, variation, null);
    }

    static PreparedTransferPath create(NetworkPackets.Visual packet, long seed, double variation, ResourceArrival arrival) {
        return new PreparedTransferPath(packet, seed, variation, arrival);
    }

    private PreparedTransferPath(NetworkPackets.Visual packet, long seed, double variation, ResourceArrival arrival) {
        List<BlockPos> path = packet.path();
        if (path.size() < 2 || path.size() > 130) throw new IllegalArgumentException("A flight requires 2 to 130 route nodes");
        nodes = new double[path.size() * NODE_STRIDE];
        legEnds = new int[path.size() - 1];
        Vec3[] centers = new Vec3[path.size()];
        Vec3[] points = new Vec3[path.size()];
        int total = 0;
        for (int i = 0; i < path.size(); i++) {
            centers[i] = path.get(i).getCenter();
            if (i > 0) legEnds[i - 1] = total += TransferVisuals.legTicks(path.get(i - 1), path.get(i));
        }
        duration = total;
        for (int i = 0; i < points.length; i++) points[i] = point(centers, i, packet.departure(), packet.arrival());
        for (int i = 0; i < points.length; i++) {
            Vec3 p = points[i];
            Vec3 v = velocity(points, i, packet.departure(), packet.arrival());
            int index = i * NODE_STRIDE;
            nodes[index] = p.x;
            nodes[index + 1] = p.y;
            nodes[index + 2] = p.z;
            nodes[index + 3] = v.x;
            nodes[index + 4] = v.y;
            nodes[index + 5] = v.z;
        }
        variationKnots = variation > 0 ? prepareVariation(centers, seed, variation) : null;
        arrivalStart = arrival == null ? 1 : arrival.finalLegStart();
        arrivalX = arrival == null ? 0 : arrival.displacement().x;
        arrivalY = arrival == null ? 0 : arrival.displacement().y;
        arrivalZ = arrival == null ? 0 : arrival.displacement().z;
    }

    Vec3 position(double progress) {
        double elapsed = Math.clamp(progress, 0, 1) * duration;
        int low = 0, high = legEnds.length - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (elapsed <= legEnds[middle]) high = middle;
            else low = middle + 1;
        }
        int leg = low;
        int start = leg == 0 ? 0 : legEnds[leg - 1];
        int ticks = legEnds[leg] - start;
        double t = Math.clamp((elapsed - start) / ticks, 0, 1);
        double t2 = t * t, t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1;
        double h10 = t3 - 2 * t2 + t;
        double h01 = -2 * t3 + 3 * t2;
        double h11 = t3 - t2;
        int a = leg * NODE_STRIDE, b = a + NODE_STRIDE;
        double x = nodes[a] * h00 + nodes[a + 3] * ticks * h10 + nodes[b] * h01 + nodes[b + 3] * ticks * h11;
        double y = nodes[a + 1] * h00 + nodes[a + 4] * ticks * h10 + nodes[b + 1] * h01 + nodes[b + 4] * ticks * h11;
        double z = nodes[a + 2] * h00 + nodes[a + 5] * ticks * h10 + nodes[b + 2] * h01 + nodes[b + 5] * ticks * h11;
        if (variationKnots != null) {
            int knot = (leg * 2 + (t < .5 ? 0 : 1)) * 3;
            double local = t < .5 ? t * 2 : t * 2 - 1;
            double blend = local * local * (3 - 2 * local);
            x += variationKnots[knot] + blend * (variationKnots[knot + 3] - variationKnots[knot]);
            y += variationKnots[knot + 1] + blend * (variationKnots[knot + 4] - variationKnots[knot + 1]);
            z += variationKnots[knot + 2] + blend * (variationKnots[knot + 5] - variationKnots[knot + 2]);
        }
        if (arrivalStart < 1) {
            double finalProgress = Math.clamp((progress - arrivalStart) / (1 - arrivalStart), 0, 1);
            double blend = finalProgress * finalProgress * (3 - 2 * finalProgress);
            x += arrivalX * blend;
            y += arrivalY * blend;
            z += arrivalZ * blend;
        }
        return new Vec3(x, y, z);
    }

    private int legTicks(int leg) {
        return legEnds[leg] - (leg == 0 ? 0 : legEnds[leg - 1]);
    }

    private static Vec3 point(Vec3[] centers, int i, TransferVisuals.Endpoint departure, TransferVisuals.Endpoint arrival) {
        Vec3 point = centers[i];
        TransferVisuals.Endpoint endpoint = i == 0 ? departure : i == centers.length - 1 ? arrival : null;
        if (endpoint != null) return point.add(endpoint.offset());
        if (i > 0 && i < centers.length - 1) {
            Vec3 before = centers[i - 1].subtract(point), after = centers[i + 1].subtract(point);
            Vec3 bend = before.normalize().add(after.normalize());
            if (bend.lengthSqr() > 1e-10)
                point = point.add(bend.normalize().scale(Math.min(.45, Math.min(before.length(), after.length()) * .25)));
        }
        return point;
    }

    private Vec3 velocity(Vec3[] points, int i, TransferVisuals.Endpoint departure, TransferVisuals.Endpoint arrival) {
        Vec3 point = points[i];
        int last = points.length - 1;
        if (i == 0 || i == last) {
            boolean start = i == 0;
            Vec3 delta = points[start ? 1 : last - 1].subtract(point);
            int ticks = legTicks(start ? 0 : last - 1);
            TransferVisuals.Endpoint endpoint = start ? departure : arrival;
            return endpoint == null ? delta.scale((start ? 1.0 : -1.0) / ticks)
                    : Vec3.atLowerCornerOf(endpoint.face().getNormal())
                            .scale((start ? 1 : -1) * Math.min(3, Math.max(.75, delta.length() * .35)) / ticks);
        }
        Vec3 before = point.subtract(points[i - 1]), after = points[i + 1].subtract(point);
        int previous = legTicks(i - 1), next = legTicks(i);
        Vec3 tangent = before.add(after).scale(1.0 / (previous + next));
        double limit = Math.min(before.length() / previous, after.length() / next);
        return tangent.length() > limit ? tangent.normalize().scale(limit) : tangent;
    }

    private static double[] prepareVariation(Vec3[] centers, long seed, double variation) {
        int last = (centers.length - 1) * 2;
        double[] knots = new double[(last + 1) * 3];
        Random random = new Random();
        for (int knot = 1; knot < last; knot++) {
            int node = knot / 2;
            Vec3 direction = (knot % 2 == 0 ? centers[node + 1].subtract(centers[node - 1])
                    : centers[node + 1].subtract(centers[node])).normalize();
            if (direction.lengthSqr() < 1e-6) direction = new Vec3(1, 0, 0);
            Vec3 side = direction.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-6) side = direction.cross(new Vec3(1, 0, 0));
            side = side.normalize();
            Vec3 up = direction.cross(side).normalize();
            random.setSeed(seed ^ (knot * 0x9e3779b97f4a7c15L));
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(random.nextDouble()) * variation;
            Vec3 offset = side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            knots[knot * 3] = offset.x;
            knots[knot * 3 + 1] = offset.y;
            knots[knot * 3 + 2] = offset.z;
        }
        return knots;
    }
}
