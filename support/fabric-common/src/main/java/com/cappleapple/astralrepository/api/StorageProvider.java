package com.cappleapple.astralrepository.api;

import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Server-thread storage adapter. Simulation must have no side effects. Returned stacks are owned by the caller. */
public interface StorageProvider {
    String id();
    /** Stable equality identity for the backing store; two views of the same store must return equal identities. */
    Object identity();
    /** Snapshot only; callers must not treat cached counts as permission to extract. */
    Map<ItemKey, Long> snapshot();
    /** Bounded reconciliation; empty means the incremental pass has not completed. */
    default java.util.Optional<Map<ItemKey, Long>> poll(int workBudget) { return java.util.Optional.of(snapshot()); }
    /**
     * Optional bounded live identity hint for transfers without aggregate stock limits.
     * Null means unsupported or no match in this probe, never proof that storage is empty.
     * The predicate must only inspect its stack. A candidate does not grant extraction permission.
     */
    default ItemKey candidate(java.util.function.Predicate<ItemStack> matches) { return null; }
    /** Returns the unaccepted remainder without changing the supplied stack. */
    ItemStack insert(ItemStack stack, boolean simulate);
    /** Returns actual extracted items, at most the requested amount. */
    ItemStack extract(ItemKey key, int amount, boolean simulate);
    boolean valid();
    /** Nominal item capacity, or -1 when the backend has no meaningful aggregate limit. */
    long capacity();
    /** Monotonic dirty version, or -1 for rate-limited polling. */
    default long version() { return -1; }
}