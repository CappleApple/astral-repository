package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.NetworkPowerProvider;
import com.cappleapple.astralrepository.api.ResourceKinds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Capacity lease on spare Create stress; never extracts FE or generates machine outputs. */
public final class CreateStressProvider implements NetworkPowerProvider {
    private static final String KINETIC = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
    private static final String NETWORK = "com.simibubi.create.content.kinetics.KineticNetwork";
    private static final Map<Object,Double> LEASED = new WeakHashMap<>();
    private final ServerLevel level;
    private final BlockEntity tile;
    private final Object network;
    private double leased;
    public CreateStressProvider(ServerLevel level, BlockEntity tile) {
        this.level = level; this.tile = tile;
        network = OptionalApi.call(tile, KINETIC, "getOrCreateNetwork");
    }
    public static boolean supports(BlockEntity tile) {
        return tile != null && OptionalApi.type(KINETIC).isInstance(tile)
                && (Boolean)OptionalApi.call(tile, KINETIC, "hasNetwork");
    }
    public String id() { return "create:stress:" + level.dimension().identifier() + ":" + tile.getBlockPos().asLong(); }
    public Object identity() { return network; }
    public Identifier resourceType() { return ResourceKinds.STRESS; }
    public Mode mode() { return Mode.CAPACITY; }
    public boolean valid() {
        return !tile.isRemoved() && level.hasChunkAt(tile.getBlockPos()) && level.getBlockEntity(tile.getBlockPos()) == tile
                && (Boolean)OptionalApi.call(tile, KINETIC, "hasNetwork")
                && !(Boolean)OptionalApi.call(tile, KINETIC, "isOverStressed")
                && ((Number)OptionalApi.call(tile, KINETIC, "getSpeed")).doubleValue() != 0
                && OptionalApi.call(tile, KINETIC, "getOrCreateNetwork") == network;
    }
    public double available() {
        if (!valid()) return 0;
        double capacity = ((Number)OptionalApi.call(network, NETWORK, "calculateCapacity")).doubleValue();
        double stress = ((Number)OptionalApi.call(network, NETWORK, "calculateStress")).doubleValue();
        return Math.max(0, capacity - stress - LEASED.getOrDefault(network, 0D));
    }
    public double acquire(double amount, boolean simulate) {
        if (!Double.isFinite(amount) || amount <= 0) return 0;
        double accepted = Math.min(amount, available());
        if (!simulate && accepted > 0) {
            LEASED.merge(network, accepted, Double::sum);
            leased += accepted;
        }
        return accepted;
    }
    public void release(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        double released = Math.min(leased, amount);
        leased -= released;
        double left = Math.max(0, LEASED.getOrDefault(network, 0D) - released);
        if (left == 0) LEASED.remove(network); else LEASED.put(network, left);
    }
}