package com.cappleapple.astralrepository.compat;

import com.cappleapple.astralrepository.api.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FluidResourceProvider implements ResourceProvider {
    private final String id;
    private final Object identity;
    private final IFluidHandler handler;
    private final BooleanSupplier valid;
    public FluidResourceProvider(String id, Object identity, IFluidHandler handler, BooleanSupplier valid) {
        this.id = id; this.identity = identity; this.handler = handler; this.valid = valid;
    }
    public String id() { return id; }
    public Object identity() { return identity; }
    public boolean valid() { return valid.getAsBoolean(); }
    public ResourceLocation resourceType() { return ResourceKinds.FLUID; }
    public String unit() { return "mB"; }
    public Map<ResourceKey, Long> snapshot() {
        Map<ResourceKey, Long> result = new LinkedHashMap<>();
        if (!valid()) return result;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (!stack.isEmpty()) result.merge(new FluidKey(stack), (long) stack.getAmount(), Math::addExact);
        }
        return Map.copyOf(result);
    }
    public long insert(ResourceKey key, long amount, boolean simulate) {
        if (!valid() || !(key instanceof FluidKey fluid) || amount <= 0) return 0;
        return handler.fill(fluid.sample().copyWithAmount((int)Math.min(Integer.MAX_VALUE, amount)), action(simulate));
    }
    public long extract(ResourceKey key, long amount, boolean simulate) {
        if (!valid() || !(key instanceof FluidKey fluid) || amount <= 0) return 0;
        return handler.drain(fluid.sample().copyWithAmount((int)Math.min(Integer.MAX_VALUE, amount)), action(simulate)).getAmount();
    }
    public long capacity() {
        long result = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) result += handler.getTankCapacity(tank);
        return result;
    }
    private static IFluidHandler.FluidAction action(boolean simulate) {
        return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }
}