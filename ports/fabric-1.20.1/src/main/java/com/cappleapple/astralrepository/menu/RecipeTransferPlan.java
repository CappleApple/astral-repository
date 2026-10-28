package com.cappleapple.astralrepository.menu;

import java.util.*;
import java.util.function.ToIntFunction;

/** Matches whole grid slots to stock without spending an overlapping ingredient twice. */
public final class RecipeTransferPlan {
    private RecipeTransferPlan() {}

    public static <K> List<K> assign(List<List<K>> ingredients, Map<K, Long> stock, int amount, ToIntFunction<K> limit) {
        if (ingredients.size() > 9 || amount < 1 || amount > 64) return null;
        List<K> assigned = new ArrayList<>(Collections.nCopies(ingredients.size(), null));
        Map<K, List<Integer>> owners = new HashMap<>();
        for (int slot = 0; slot < ingredients.size(); slot++) {
            if (!ingredients.get(slot).isEmpty() && !place(slot, ingredients, stock, amount, limit, assigned, owners, new HashSet<>())) return null;
        }
        return Collections.unmodifiableList(assigned);
    }

    private static <K> boolean place(int slot, List<List<K>> ingredients, Map<K, Long> stock, int amount,
                                    ToIntFunction<K> limit, List<K> assigned, Map<K, List<Integer>> owners, Set<K> seen) {
        for (K key : ingredients.get(slot)) {
            if (limit.applyAsInt(key) < amount || !seen.add(key)) continue;
            List<Integer> used = owners.computeIfAbsent(key, ignored -> new ArrayList<>());
            long capacity = stock.getOrDefault(key, 0L) / amount;
            if (used.size() < capacity) { used.add(slot); assigned.set(slot, key); return true; }
            for (int i = 0; i < used.size(); i++) {
                int previous = used.get(i);
                if (place(previous, ingredients, stock, amount, limit, assigned, owners, seen)) {
                    used.set(i, slot); assigned.set(slot, key); return true;
                }
            }
        }
        return false;
    }
}
