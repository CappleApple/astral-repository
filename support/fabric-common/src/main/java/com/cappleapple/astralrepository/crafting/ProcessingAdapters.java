package com.cappleapple.astralrepository.crafting;

import java.util.*;
import java.util.function.Supplier;

/** Register optional adapter factories during common setup, before networks are created. */
public final class ProcessingAdapters {
    private static final Map<String, Supplier<ProcessingAdapter>> FACTORIES = new LinkedHashMap<>();
    private ProcessingAdapters() {}
    public static synchronized void register(String id, Supplier<ProcessingAdapter> factory) {
        if (FACTORIES.putIfAbsent(id, Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate processing adapter " + id);
    }
    static synchronized List<ProcessingAdapter> create() {
        List<ProcessingAdapter> result = new ArrayList<>();
        result.add(new VanillaProcessingAdapter());
        result.add(new InventoryProcessingAdapter());
        FACTORIES.forEach((id, factory) -> { try { result.add(factory.get()); } catch (RuntimeException failure) { CraftingService.LOGGER.error("Processing adapter {} could not initialize", id, failure); } });
        return result;
    }
}
