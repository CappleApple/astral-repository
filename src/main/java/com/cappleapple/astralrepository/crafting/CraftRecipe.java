package com.cappleapple.astralrepository.crafting;

import java.util.List;
import java.util.Objects;

/** Immutable production knowledge supplied by an adapter, independent of Minecraft world state. */
public record CraftRecipe<K>(String id, K output, long outputCount, String process,
                             List<Ingredient<K>> ingredients, int priority, int durationTicks, long returnedSeedCount) {
    public CraftRecipe(String id, K output, long outputCount, String process, List<Ingredient<K>> ingredients, int priority, int durationTicks) {
        this(id, output, outputCount, process, ingredients, priority, durationTicks, 0);
    }
    public CraftRecipe {
        Objects.requireNonNull(id);
        Objects.requireNonNull(output);
        Objects.requireNonNull(process);
        ingredients = List.copyOf(ingredients);
        if (outputCount < 1 || durationTicks < 1 || ingredients.isEmpty() || returnedSeedCount < 0)
            throw new IllegalArgumentException("A recipe needs inputs, positive output and duration");
        Math.addExact(outputCount, returnedSeedCount);
        if (returnedSeedCount > 0 && ingredients.stream().filter(input -> input.alternatives().contains(output)).mapToLong(Ingredient::count).reduce(0, Math::addExact) != returnedSeedCount)
            throw new IllegalArgumentException("Returned seed must match the recipe's real self inputs");
    }
    public long grossOutputCount() { return Math.addExact(outputCount, returnedSeedCount); }
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
