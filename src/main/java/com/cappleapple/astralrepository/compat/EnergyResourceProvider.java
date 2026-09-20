package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceLocation;
import com.cappleapple.astralrepository.platform.energy.IEnergyStorage;

public final class EnergyResourceProvider implements ResourceProvider {
    private final String id;
    private final Object identity;
    private final IEnergyStorage handler;
    private final BooleanSupplier valid;
    public EnergyResourceProvider(String id, Object identity, IEnergyStorage handler, BooleanSupplier valid) {
        this.id = id; this.identity = identity; this.handler = handler; this.valid = valid;
    }
    public String id() { return id; }
    public Object identity() { return identity; }
    public boolean valid() { return valid.getAsBoolean(); }
    public ResourceLocation resourceType() { return ResourceKinds.ENERGY; }
    public String unit() { return "FE"; }
    public Map<ResourceKey, Long> snapshot() {
        return valid() ? Map.of(ResourceKinds.FE, (long)handler.getEnergyStored()) : Map.of();
    }
    public long insert(ResourceKey key, long amount, boolean simulate) {
        return valid() && ResourceKinds.FE.equals(key) && amount > 0
                ? handler.receiveEnergy((int)Math.min(Integer.MAX_VALUE, amount), simulate) : 0;
    }
    public long extract(ResourceKey key, long amount, boolean simulate) {
        return valid() && ResourceKinds.FE.equals(key) && amount > 0
                ? handler.extractEnergy((int)Math.min(Integer.MAX_VALUE, amount), simulate) : 0;
    }
    public long capacity() { return handler.getMaxEnergyStored(); }
}