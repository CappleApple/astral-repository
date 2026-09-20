package com.cappleapple.astralrepository.network;

import java.util.*;

/** Incremental count/location index. Search never invokes a live inventory handler. */
public final class NetworkInventoryIndex<K, P> {
    private final Map<P, Map<K, Long>> providers = new HashMap<>();
    private final Map<K, Long> totals = new HashMap<>();
    private final Map<K, Set<P>> locations = new HashMap<>();
    private long version;

    public void update(P provider, Map<K, Long> items) {
        Map<K, Long> sanitized = new HashMap<>();
        items.forEach((key, count) -> { if (count > 0) sanitized.put(key, count); });
        if (sanitized.equals(providers.get(provider))) return;
        remove(provider);
        providers.put(provider, sanitized);
        sanitized.forEach((key, count) -> {
            totals.merge(key, count, NetworkInventoryIndex::saturatingAdd);
            locations.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(provider);
        });
        version++;
    }
    public void remove(P provider) {
        Map<K, Long> previous = providers.remove(provider);
        if (previous == null) return;
        previous.forEach((key, count) -> {
            Set<P> sources = locations.get(key);
            if (sources != null) { sources.remove(provider); if (sources.isEmpty()) locations.remove(key); }
            long total = totals.getOrDefault(key, 0L);
            long remaining = Math.max(0, total - count);
            if (total == Long.MAX_VALUE) {
                remaining = 0;
                for (P source : locations.getOrDefault(key, Set.of()))
                    remaining = saturatingAdd(remaining, providers.get(source).get(key));
            }
            if (remaining == 0) totals.remove(key); else totals.put(key, remaining);
        });
        version++;
    }
    /** Apply a committed quantity change without rebuilding unrelated keys or their locations. */
    public void adjust(P provider, K key, long delta) {
        Map<K, Long> items = providers.get(provider);
        long previous = items == null ? 0 : items.getOrDefault(key, 0L);
        long value = delta >= 0 ? saturatingAdd(previous, delta) : delta < -previous ? 0 : previous + delta;
        if (value == previous) return;
        if (items == null) { items = new HashMap<>(); providers.put(provider, items); }
        if (value == 0) {
            items.remove(key);
            Set<P> sources = locations.get(key);
            if (sources != null) { sources.remove(provider); if (sources.isEmpty()) locations.remove(key); }
        } else {
            items.put(key, value);
            locations.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(provider);
        }
        long total = totals.getOrDefault(key, 0L);
        if (total == Long.MAX_VALUE && value < previous) {
            // Saturated totals cannot be decremented arithmetically: recover their actual sum.
            total = 0;
            for (P source : locations.getOrDefault(key, Set.of()))
                total = saturatingAdd(total, providers.get(source).get(key));
        } else total = value >= previous ? saturatingAdd(total, value - previous) : Math.max(0, total - (previous - value));
        if (total == 0) totals.remove(key); else totals.put(key, total);
        version++;
    }
    public Map<K, Long> snapshot() { return Map.copyOf(totals); }
    public Map<K, Long> provider(P provider) { return Collections.unmodifiableMap(providers.getOrDefault(provider, Map.of())); }
    public List<P> locations(K key) { return List.copyOf(locations.getOrDefault(key, Set.of())); }
    public long count(K key) { return totals.getOrDefault(key, 0L); }
    public long version() { return version; }
    public int size() { return totals.size(); }
    public static long saturatingAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
}
