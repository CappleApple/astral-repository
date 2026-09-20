package com.cappleapple.astralrepository.crafting;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Topologically ordered DAG. Reservations contain only resources taken from existing storage. */
public record CraftPlan<K>(K target, long count, Map<K, Long> reservations, List<Node<K>> nodes) {
    public CraftPlan {
        reservations = Map.copyOf(reservations);
        nodes = List.copyOf(nodes);
    }
    public record Selection<K>(K key, long count) {}
    public record Node<K>(int id, CraftRecipe<K> recipe, List<Selection<K>> selected, Set<Integer> dependencies) {
        public Node {
            selected = List.copyOf(selected);
            dependencies = Set.copyOf(dependencies);
        }
        public Map<K, Long> inputs() {
            Map<K, Long> result = new java.util.LinkedHashMap<>();
            selected.forEach(input -> result.merge(input.key(), input.count(), Math::addExact));
            return Map.copyOf(result);
        }
    }
}
