package com.cappleapple.astralrepository.api;

import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.cappleapple.astralrepository.platform.fluids.FluidStack;

/** Fluid identity retaining data components. Quantities are measured in millibuckets. */
public final class FluidKey implements ResourceKey {
    private final FluidStack sample;
    private final int hash;
    public FluidKey(FluidStack stack) {
        if (Objects.requireNonNull(stack).isEmpty()) throw new IllegalArgumentException("Empty fluid identity");
        sample = stack.copyWithAmount(1);
        hash = FluidStack.hashFluidAndComponents(sample);
    }
    public FluidStack sample() { return sample.copy(); }
    public Identifier id() { return BuiltInRegistries.FLUID.getKey(sample.getFluid()); }
    public Identifier type() { return ResourceKinds.FLUID; }
    @Override public boolean equals(Object other) {
        return other instanceof FluidKey key && FluidStack.isSameFluidSameComponents(sample, key.sample);
    }
    @Override public int hashCode() { return hash; }
}