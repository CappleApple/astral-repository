package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.port.storage.IFluidHandler;
import com.cappleapple.astralrepository.port.storage.IItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** Typed transactional capability bridge used by native backends on26.x. */
final class NativePlatformPorts {
    private static final java.lang.ref.ReferenceQueue<Object> RELEASED = new java.lang.ref.ReferenceQueue<>();
    private static final java.util.Map<Identity, java.lang.ref.WeakReference<Object>> FACADES = new java.util.HashMap<>();
    private static final class Identity extends java.lang.ref.WeakReference<Object> {
        final int hash; final boolean fluid;
        Identity(Object owner, boolean fluid) { super(owner, RELEASED); this.fluid = fluid; hash = System.identityHashCode(owner) * 31 + Boolean.hashCode(fluid); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) { return this == other || other instanceof Identity identity && fluid == identity.fluid && get() != null && get() == identity.get(); }
    }
    private NativePlatformPorts() {}
    static IItemHandler items(ServerLevel level, BlockPos position, Direction side) {
        if (!level.hasChunkAt(position)) return null;
        var handler = level.getCapability(Capabilities.Item.BLOCK, position, side);
        return handler == null ? null : items(handler);
    }
    static IFluidHandler fluids(ServerLevel level, BlockPos position, Direction side) {
        if (!level.hasChunkAt(position)) return null;
        var handler = level.getCapability(Capabilities.Fluid.BLOCK, position, side);
        return handler == null ? null : fluids(handler);
    }
    static IItemHandler items(ResourceHandler<ItemResource> handler) { return facade(handler, false, () -> IItemHandler.of(handler)); }
    static IFluidHandler fluids(ResourceHandler<FluidResource> handler) { return facade(handler, true, () -> IFluidHandler.of(handler)); }
    @SuppressWarnings("unchecked")
    private static synchronized <T> T facade(Object owner, boolean fluid, java.util.function.Supplier<T> create) {
        for (var released = RELEASED.poll(); released != null; released = RELEASED.poll()) FACADES.remove(released);
        var key = new Identity(owner, fluid); var cached = FACADES.get(key);
        Object existing = cached == null ? null : cached.get();
        if (existing != null) return (T)existing;
        T result = create.get(); FACADES.put(key, new java.lang.ref.WeakReference<>(result)); return result;
    }
}
