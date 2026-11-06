package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Provenance of real item entities emitted by an exclusively claimed native machine tick. */
public final class NativeWorldOutputs {
    public record Claim(UUID request, GlobalPos processor) {}
    private static final ThreadLocal<Claim> CURRENT = new ThreadLocal<>();
    private static final Map<ItemEntity, Claim> OWNERS = new WeakHashMap<>();
    private static final Map<ItemEntity, Claim> CONSUMED = new WeakHashMap<>();
    public interface WorldInput extends NativeMachineBackend.Port { boolean processed(); }
    private NativeWorldOutputs() {}

    public static void tick(Level level, BlockPos position, Runnable original) {
        if (!CraftingService.hasProcessorClaims()) { original.run(); return; }
        Claim previous = CURRENT.get();
        Claim owner = level instanceof ServerLevel server ? CraftingService.nativeOutputClaim(server, position) : null;
        CURRENT.set(owner);
        try { original.run(); }
        finally { CURRENT.set(previous); }
    }
    public static void spawned(ItemEntity item) {
        Claim claim = CURRENT.get();
        if (claim != null && item.level() instanceof ServerLevel level && !item.getItem().isEmpty()
                && claim.equals(CraftingService.nativeOutputClaim(level, claim.processor().pos()))) OWNERS.put(item, claim);
    }
    public static void discarded(ItemEntity item) {
        Claim current = CURRENT.get(); if (current == null) return;
        Claim owner = OWNERS.get(item);
        if (current != null && current.equals(owner)) CONSUMED.put(item, owner);
    }
    /** Keep owned and preexisting entity stacks physically separate until collection or cancellation. */
    public static boolean exclusivelyOwned(ItemEntity item) {
        if (!CraftingService.hasProcessorClaims()) return false;
        Claim claim = OWNERS.get(item);
        return claim != null && item.level() instanceof ServerLevel level
                && claim.equals(CraftingService.nativeOutputClaim(level, claim.processor().pos()));
    }
    private static List<ItemEntity> owned(ServerLevel level, Claim claim, Set<UUID> ignored) {
        return OWNERS.entrySet().stream().filter(entry -> entry.getValue().equals(claim))
                .map(Map.Entry::getKey).filter(item -> item.level() == level && item.isAlive() && !ignored.contains(item.getUUID()))
                .sorted(Comparator.comparing(ItemEntity::getUUID)).toList();
    }
    public static NativeMachineBackend.Port output(ServerLevel level, GlobalPos position) {
        Claim claim = CraftingService.nativeOutputClaim(level, position.pos());
        if (claim == null) throw new IllegalStateException("Native dropped output needs an exclusive processor claim");
        Set<UUID> existing = new HashSet<>(); owned(level, claim, Set.of()).forEach(item -> existing.add(item.getUUID()));
        return NativeMachinePorts.callbacks(List.of("native-world-output", claim), false, true, () -> {
            Map<ResourceKey, Long> result = new LinkedHashMap<>();
            for (ItemEntity item : owned(level, claim, existing)) result.merge(new ItemKey(item.getItem()), (long) item.getItem().getCount(), Math::addExact);
            return Map.copyOf(result);
        }, (key, amount, simulate) -> 0, (key, amount, simulate) -> {
            long taken = 0;
            for (ItemEntity item : owned(level, claim, existing)) {
                ItemStack stack = item.getItem();
                if (!new ItemKey(stack).equals(key)) continue;
                int count = (int) Math.min(amount - taken, stack.getCount());
                if (count <= 0) break;
                if (!simulate) {
                    if (count == stack.getCount()) { OWNERS.remove(item); item.discard(); }
                    else { ItemStack remainder = stack.copy(); remainder.shrink(count); item.setItem(remainder); }
                }
                taken += count;
            }
            return taken;
        });
    }
    /** Transfers real job-owned item inputs into a configured native world-processing space. */
    public static WorldInput input(ServerLevel level, GlobalPos processor, Vec3 location,
                                                  java.util.function.Predicate<ItemStack> accepts) {
        List<ItemEntity> inserted = new ArrayList<>();
        var delegate = NativeMachinePorts.callbacks(List.of("native-world-input", processor, location), true, false, () -> {
            Map<ResourceKey, Long> result = new LinkedHashMap<>();
            for (ItemEntity item : inserted) if (item.isAlive() && !item.getItem().isEmpty())
                result.merge(new ItemKey(item.getItem()), (long) item.getItem().getCount(), Math::addExact);
            return Map.copyOf(result);
        }, (key, amount, simulate) -> {
            if (!(key instanceof ItemKey value) || inserted.stream().anyMatch(ItemEntity::isAlive)) return 0;
            int count = (int) Math.min(amount, value.sample().getMaxStackSize());
            ItemStack stack = value.sample(); stack.setCount(count);
            if (!accepts.test(stack)) return 0;
            Claim claim = CraftingService.nativeOutputClaim(level, processor.pos()); if (claim == null) return 0;
            if (!simulate) {
                ItemEntity entity = new ItemEntity(level, location.x, location.y, location.z, stack);
                entity.setNoGravity(true);
                entity.setExtendedLifetime();
                entity.setDeltaMovement(Vec3.ZERO);
                if (!level.addFreshEntity(entity)) return 0;
                inserted.add(entity); OWNERS.put(entity, claim);
            }
            return count;
        }, (key, amount, simulate) -> {
            long taken = 0;
            for (ItemEntity entity : inserted) if (entity.isAlive() && !entity.getItem().isEmpty() && new ItemKey(entity.getItem()).equals(key)) {
                int count = (int) Math.min(amount - taken, entity.getItem().getCount()); if (count <= 0) break;
                if (!simulate) {
                    if (count == entity.getItem().getCount()) { OWNERS.remove(entity); entity.discard(); }
                    else { ItemStack remainder = entity.getItem().copy(); remainder.shrink(count); entity.setItem(remainder); }
                }
                taken += count;
            }
            return taken;
        });
        return new WorldInput() {
            public Object identity() { return delegate.identity(); }
            public boolean input() { return true; }
            public boolean output() { return false; }
            public Map<ResourceKey, Long> snapshot() { return delegate.snapshot(); }
            public long insert(ResourceKey key, long amount, boolean simulate) { return delegate.insert(key, amount, simulate); }
            public long extract(ResourceKey key, long amount, boolean simulate) { return delegate.extract(key, amount, simulate); }
            public boolean processed() { return !inserted.isEmpty() && inserted.stream().allMatch(CONSUMED::containsKey); }
            public void tickOwned() {
                for (ItemEntity entity : inserted) if (entity.isAlive()) {
                    entity.setExtendedLifetime();
                    entity.setDeltaMovement(Vec3.ZERO); entity.setPos(location.x, location.y, location.z);
                }
            }
        };
    }
}
