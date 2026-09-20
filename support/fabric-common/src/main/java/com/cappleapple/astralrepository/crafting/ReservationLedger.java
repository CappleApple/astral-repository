package com.cappleapple.astralrepository.crafting;

import java.util.*;

/** Claim ledger; actual extraction must still be revalidated on the server thread. */
public final class ReservationLedger<K> {
    private final Map<UUID, Map<K, Long>> claims = new HashMap<>();
    private final Map<K, Long> totals = new HashMap<>();
    public synchronized boolean reserve(UUID request, Map<K, Long> wanted, Map<K, Long> snapshot) {
        if (claims.containsKey(request)) throw new IllegalArgumentException("Request already reserved");
        for (var entry : wanted.entrySet()) {
            if (entry.getValue() < 0 || entry.getValue() > snapshot.getOrDefault(entry.getKey(), 0L) - totals.getOrDefault(entry.getKey(), 0L))
                return false;
        }
        Map<K, Long> copy = Map.copyOf(wanted);
        copy.forEach((key, count) -> totals.merge(key, count, Math::addExact));
        claims.put(request, copy);
        return true;
    }
    public synchronized Map<K, Long> release(UUID request) {
        Map<K, Long> released = claims.remove(request);
        if (released == null) return Map.of();
        released.forEach((key, count) -> {
            long remaining = totals.getOrDefault(key, 0L) - count;
            if (remaining == 0) totals.remove(key); else totals.put(key, remaining);
        });
        return released;
    }
    public synchronized Map<K, Long> available(Map<K, Long> snapshot) {
        Map<K, Long> result = new LinkedHashMap<>();
        snapshot.forEach((key, count) -> {
            long available = count - totals.getOrDefault(key, 0L);
            if (available > 0) result.put(key, available);
        });
        return Map.copyOf(result);
    }
    public synchronized int requests() { return claims.size(); }
}
