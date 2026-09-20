package com.cappleapple.astralrepository.network;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;

/** A fair sample of this tick, with no queue of delayed cosmetic work. */
final class VisualSampler<T> {
    private final int capacity;
    private final SplittableRandom random;
    private final List<T> entries = new ArrayList<>();
    private long seen;

    VisualSampler(int capacity, long seed) {
        if (capacity < 1) throw new IllegalArgumentException("Invalid visual capacity");
        this.capacity = capacity;
        this.random = new SplittableRandom(seed);
    }

    boolean offer(Supplier<T> entry) {
        int index = reserve();
        if (index < 0) return false;
        put(index, entry.get());
        return true;
    }

    int reserve() {
        long index = seen++;
        if (index >= capacity) index = random.nextLong(seen);
        return index < capacity ? (int) index : -1;
    }

    void put(int index, T value) {
        if (index == entries.size()) entries.add(value);
        else entries.set(index, value);
    }

    List<T> entries() { return entries; }
    long seen() { return seen; }
}
