package com.cappleapple.astralrepository.crafting;

import java.util.List;
import java.util.Objects;

/** Immutable production knowledge supplied by an adapter, independent of Minecraft world state. */
public record CraftRecipe<K>(String id, K output, long outputCount, String process,
                             List<Ingredient<K>> ingredients, int priority, int durationTicks) {
    public CraftRecipe {
        Objects.requireNonNull(id);
        Objects.requireNonNull(output);
        Objects.requireNonNull(process);
        ingredients = List.copyOf(ingredients);
        if (outputCount < 1 || durationTicks < 1 || ingredients.isEmpty())
            throw new IllegalArgumentException("A recipe needs inputs, positive output and duration");
    }
    public record Ingredient<K>(List<K> alternatives, long count, String tag) {
        public Ingredient(List<K> alternatives,long count){this(alternatives,count,"");}
        public Ingredient {
            Objects.requireNonNull(tag);
            alternatives = List.copyOf(alternatives);
            if (alternatives.isEmpty() || count < 1) throw new IllegalArgumentException("Empty ingredient");
        }
        public static <K> Ingredient<K> of(K key) { return new Ingredient<>(List.of(key), 1); }
    }
}
