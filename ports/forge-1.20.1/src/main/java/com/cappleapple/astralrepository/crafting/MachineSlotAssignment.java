package com.cappleapple.astralrepository.crafting;

import java.util.*;
import java.util.function.BiPredicate;

/** Bounded matching keeps distinct recipe ingredients in distinct machine slots. */
public final class MachineSlotAssignment {
    private MachineSlotAssignment() {}
    public static <T> List<Integer> assign(List<Integer> slots, List<T> ingredients, BiPredicate<Integer, T> accepts) {
        if (ingredients.isEmpty() || ingredients.size() > 64 || ingredients.size() > slots.size()) return null;
        var assigned = new ArrayList<Integer>();
        return visit(List.copyOf(new LinkedHashSet<>(slots)), ingredients, accepts, 0, assigned, new int[]{4096}) ? List.copyOf(assigned) : null;
    }
    private static <T> boolean visit(List<Integer> slots, List<T> ingredients, BiPredicate<Integer, T> accepts,
                                     int index, List<Integer> assigned, int[] budget) {
        if (index == ingredients.size()) return true;
        for (int slot : slots) {
            if (--budget[0] < 0) return false;
            if (assigned.contains(slot) || !accepts.test(slot, ingredients.get(index))) continue;
            assigned.add(slot);
            if (visit(slots, ingredients, accepts, index + 1, assigned, budget)) return true;
            assigned.remove(assigned.size() - 1);
        }
        return false;
    }
}
