package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Loader-neutral physical port helpers; native backends remain responsible for role discovery. */
public final class NativeMachinePorts {
    private NativeMachinePorts() {}
    @FunctionalInterface public interface Transfer { long apply(ResourceKey key, long amount, boolean simulate); }
    private record SlotIdentity(Object owner, int index, String kind) {
        @Override public boolean equals(Object other) {
            return other instanceof SlotIdentity key && key.owner == owner && key.index == index && key.kind.equals(kind);
        }
        @Override public int hashCode() { return Objects.hash(System.identityHashCode(owner), index, kind); }
    }
    public static Object identity(Object owner, int index, String kind) { return new SlotIdentity(owner, index, kind); }

    public static NativeMachineBackend.Port callbacks(Object identity, boolean input, boolean output,
            Supplier<Map<ResourceKey, Long>> snapshot, Transfer insert, Transfer extract) {
        return new NativeMachineBackend.Port() {
            public Object identity() { return identity; }
            public boolean input() { return input; }
            public boolean output() { return output; }
            public Map<ResourceKey, Long> snapshot() {
                Map<ResourceKey, Long> copy = new LinkedHashMap<>();
                snapshot.get().forEach((key, amount) -> { if (amount > 0) copy.merge(Objects.requireNonNull(key), amount, Math::addExact); });
                return Map.copyOf(copy);
            }
            public long insert(ResourceKey key, long amount, boolean simulate) { return input && amount > 0 ? insert.apply(key, amount, simulate) : 0; }
            public long extract(ResourceKey key, long amount, boolean simulate) { return amount > 0 ? extract.apply(key, amount, simulate) : 0; }
        };
    }

    public static List<NativeMachineBackend.Port> items(Object handler, List<Integer> slots, boolean input, boolean output) {
        return slots.stream().map(slot -> itemSlot(handler, slot, input, output)).toList();
    }
    public static NativeMachineBackend.Port itemSlot(Object handler, int slot, boolean input, boolean output) {
        Method get = method(handler, "getStackInSlot", int.class);
        Method insert = method(handler, "insertItem", int.class, ItemStack.class, boolean.class);
        Method extract = method(handler, "extractItem", int.class, int.class, boolean.class);
        Method limit = method(handler, "getSlotLimit", int.class);
        return callbacks(identity(handler, slot, "item"), input, output, () -> {
            ItemStack stack = (ItemStack) call(get, handler, slot);
            return stack.isEmpty() ? Map.of() : Map.of(new ItemKey(stack), (long) stack.getCount());
        }, (key, amount, simulate) -> {
            if (!(key instanceof ItemKey item)) return 0;
            int count = (int) Math.min(Math.min(amount, Integer.MAX_VALUE), ((Number) call(limit, handler, slot)).longValue());
            if (count <= 0) return 0;
            ItemStack offered = item.sample().copyWithCount(count);
            ItemStack remainder = (ItemStack) call(insert, handler, slot, offered, simulate);
            if (!remainder.isEmpty() && (!new ItemKey(remainder).equals(key) || remainder.getCount() > count))
                throw new IllegalStateException("Native item insertion returned an invalid remainder");
            return count - remainder.getCount();
        }, (key, amount, simulate) -> {
            if (!(key instanceof ItemKey)) return 0;
            ItemStack before = (ItemStack) call(get, handler, slot);
            if (before.isEmpty() || !new ItemKey(before).equals(key)) return 0;
            int count = (int) Math.min(amount, before.getCount());
            ItemStack actual = (ItemStack) call(extract, handler, slot, count, simulate);
            if (actual.isEmpty()) return 0;
            if (!new ItemKey(actual).equals(key) || actual.getCount() > count) {
                if (simulate) throw new IllegalStateException("Native item extraction simulation returned a different identity");
                throw new ExtractedResources("Native item extraction returned a different identity", Map.of(new ItemKey(actual), (long) actual.getCount()));
            }
            return actual.getCount();
        });
    }

    public static NativeMachineBackend.Port streaming(NativeMachineBackend.Port delegate) {
        return new NativeMachineBackend.Port() {
            public Object identity() { return delegate.identity(); }
            public boolean input() { return delegate.input(); }
            public boolean output() { return delegate.output(); }
            public Map<ResourceKey, Long> retainedInputBaseline() { return delegate.retainedInputBaseline(); }
            public boolean streamingOutput() { return true; }
            public void tickOwned() { delegate.tickOwned(); }
            public Map<ResourceKey, Long> snapshot() { return delegate.snapshot(); }
            public long insert(ResourceKey key, long amount, boolean simulate) { return delegate.insert(key, amount, simulate); }
            public long extract(ResourceKey key, long amount, boolean simulate) { return delegate.extract(key, amount, simulate); }
        };
    }
    /** Protect actual native setup in an otherwise owned input port. */
    public static NativeMachineBackend.Port retainedInput(NativeMachineBackend.Port delegate, Map<ResourceKey, Long> baseline) {
        if (!delegate.input()) throw new IllegalArgumentException("Retained setup requires an input port");
        Map<ResourceKey, Long> retained = Map.copyOf(baseline);
        if (retained.values().stream().anyMatch(amount -> amount <= 0))
            throw new IllegalArgumentException("Retained setup quantities must be positive");
        return new NativeMachineBackend.Port() {
            public Object identity() { return delegate.identity(); }
            public boolean input() { return delegate.input(); }
            public boolean output() { return delegate.output(); }
            public Map<ResourceKey, Long> retainedInputBaseline() { return retained; }
            public boolean streamingOutput() { return delegate.streamingOutput(); }
            public void tickOwned() { delegate.tickOwned(); }
            public Map<ResourceKey, Long> snapshot() { return delegate.snapshot(); }
            public long insert(ResourceKey key, long amount, boolean simulate) { return delegate.insert(key, amount, simulate); }
            public long extract(ResourceKey key, long amount, boolean simulate) { return delegate.extract(key, amount, simulate); }
        };
    }
    /** Forge-energy units from the native public energy-storage contract, without synthetic power. */
    public static NativeMachineBackend.Port energy(Object storage, boolean input, boolean output) {
        Method stored = method(storage, "getEnergyStored");
        Method receive = method(storage, "receiveEnergy", int.class, boolean.class);
        Method extract = method(storage, "extractEnergy", int.class, boolean.class);
        return callbacks(identity(storage, 0, "energy"), input, output, () -> {
            long amount = ((Number) call(stored, storage)).longValue();
            return amount > 0 ? Map.of(ResourceKinds.FE, amount) : Map.of();
        }, (key, amount, simulate) -> key.equals(ResourceKinds.FE)
                ? ((Number) call(receive, storage, (int) Math.min(amount, Integer.MAX_VALUE), simulate)).longValue() : 0,
            (key, amount, simulate) -> key.equals(ResourceKinds.FE)
                ? ((Number) call(extract, storage, (int) Math.min(amount, Integer.MAX_VALUE), simulate)).longValue() : 0);
    }

    /** A native single-tank IFluidTank. Aggregate multi-tank handlers need per-tank native callbacks. */
    public static NativeMachineBackend.Port fluidTank(Object tank, boolean input, boolean output) {
        Method get = method(tank, "getFluid");
        Object empty = call(get, tank);
        Class<?> stackType = empty.getClass();
        Class<?> action;
        try { action = Class.forName(stackType.getPackageName() + ".capability.IFluidHandler$FluidAction", false, tank.getClass().getClassLoader()); }
        catch (ClassNotFoundException missing) { throw new IllegalArgumentException("Native fluid action contract unavailable", missing); }
        Method fill = method(tank, "fill", stackType, action);
        Method drain = method(tank, "drain", stackType, action);
        Class<?> finalAction = action;
        return callbacks(identity(tank, 0, "fluid"), input, output, () -> {
            Object stack = call(get, tank);
            long amount = MekanismResourceSupport.amount(stack);
            return amount <= 0 ? Map.of() : Map.of(MekanismResourceSupport.key(stack), amount);
        }, (key, amount, simulate) -> {
            if (!(key instanceof FluidKey)) return 0;
            Object stack = MekanismResourceSupport.sample(key, Math.min(amount, Integer.MAX_VALUE));
            return ((Number) call(fill, tank, stack, action(finalAction, simulate))).longValue();
        }, (key, amount, simulate) -> {
            if (!(key instanceof FluidKey)) return 0;
            Object offered = MekanismResourceSupport.sample(key, Math.min(amount, Integer.MAX_VALUE));
            Object actual = call(drain, tank, offered, action(finalAction, simulate));
            long count = MekanismResourceSupport.amount(actual);
            if (count > 0 && !MekanismResourceSupport.key(actual).equals(key)) {
                if (simulate) throw new IllegalStateException("Native fluid simulation returned a different identity");
                throw new ExtractedResources("Native fluid extraction returned a different identity", Map.of(MekanismResourceSupport.key(actual), count));
            }
            return count;
        });
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static Object action(Class<?> type, boolean simulate) {
        return Enum.valueOf((Class) type, simulate ? "SIMULATE" : "EXECUTE");
    }
    private static Method method(Object target, String name, Class<?>... parameters) {
        try { Method result = target.getClass().getMethod(name, parameters); result.setAccessible(true); return result; }
        catch (ReflectiveOperationException unavailable) { throw new IllegalArgumentException("Native port contract unavailable: " + name, unavailable); }
    }
    private static Object call(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Native port call failed", failed.getCause());
        } catch (ReflectiveOperationException unavailable) { throw new IllegalStateException("Native port access failed", unavailable); }
    }
    /** Exact resources already extracted are retained even if a native handler violates its identity contract. */
    public static final class ExtractedResources extends IllegalStateException {
        private final Map<ResourceKey, Long> resources;
        public ExtractedResources(String message, Map<ResourceKey, Long> resources) { super(message); this.resources = Map.copyOf(resources); }
        public Map<ResourceKey, Long> resources() { return resources; }
    }
}
