package com.cappleapple.astralrepository.crafting;

import java.util.Set;

/** Inventory adapters prefix recipe IDs with their processing-rule ID. */
public final class RecipeExclusions {
    public static boolean allows(String recipe, Set<String> excluded) {
        if (excluded.contains(recipe)) return false;
        return excluded.stream().noneMatch(id -> recipe.endsWith("/" + id));
    }
    /** A composed native operation is blocked when any actual source recipe is blocked. */
    public static boolean allows(String recipe, java.util.Collection<String> sources, Set<String> excluded) {
        return allows(recipe, excluded) && sources.stream().allMatch(source -> allows(source, excluded));
    }
    private RecipeExclusions() {}
}
