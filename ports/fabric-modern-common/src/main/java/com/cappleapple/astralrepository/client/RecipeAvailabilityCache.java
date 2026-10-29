package com.cappleapple.astralrepository.client;

import java.util.*;
import java.util.function.*;

/** Detached stock and recipe answers for one exact client inventory state. */
public final class RecipeAvailabilityCache<K, B, R> {
    private final Map<K, Long> stock;
    private final Map<B, List<K>> index;
    private final Map<R, Boolean> answers = new IdentityHashMap<>();

    public RecipeAvailabilityCache(Map<K, Long> stock, Function<K, B> bucket) {
        Map<K, Long> positive = new LinkedHashMap<>();
        stock.forEach((key, count) -> { if (count > 0) positive.put(key, count); });
        this.stock = Collections.unmodifiableMap(positive);
        Map<B, List<K>> indexed = new HashMap<>();
        positive.keySet().forEach(key -> indexed.computeIfAbsent(bucket.apply(key), ignored -> new ArrayList<>()).add(key));
        indexed.replaceAll((key, values) -> List.copyOf(values));
        this.index = Collections.unmodifiableMap(indexed);
    }

    public Map<K, Long> stock() { return stock; }

    /** Null buckets retain full predicate matching for custom ingredients. */
    public List<K> candidates(Collection<B> buckets, Predicate<K> predicate) {
        Collection<K> candidates;
        if (buckets == null) candidates = stock.keySet();
        else {
            Set<K> selected = new LinkedHashSet<>();
            for (B bucket : buckets) selected.addAll(index.getOrDefault(bucket, List.of()));
            candidates = selected;
        }
        return candidates.stream().filter(predicate).toList();
    }

    /** Only call for recipes whose ingredients do not depend on external mutable state. */
    public boolean available(R recipe, BooleanSupplier compute) {
        Boolean answer = answers.get(recipe);
        if (answer == null) { answer = compute.getAsBoolean(); answers.put(recipe, answer); }
        return answer;
    }
}
