package com.cappleapple.astralrepository.api;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Immutable provenance token. Preserve it across provider bridges to reject recursive crafting requests. */
public record CraftingContext(UUID requestId, Set<String> visitedProviders, int maximumDepth) {
    public CraftingContext {
        visitedProviders = Set.copyOf(visitedProviders);
        if (maximumDepth < 1) throw new IllegalArgumentException("maximumDepth must be positive");
    }
    public static CraftingContext root() { return new CraftingContext(UUID.randomUUID(), Set.of(), 32); }
    public CraftingContext enter(String provider) {
        if (visitedProviders.contains(provider)) throw new IllegalStateException("Crafting provider cycle: " + provider);
        if (visitedProviders.size() >= maximumDepth) throw new IllegalStateException("Crafting provider depth exceeded");
        Set<String> next = new LinkedHashSet<>(visitedProviders);
        next.add(provider);
        return new CraftingContext(requestId, next, maximumDepth);
    }
}