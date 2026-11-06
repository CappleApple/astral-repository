package com.cappleapple.astralrepository.crafting;

import java.util.*;
import java.util.function.Supplier;

/** Register optional adapter factories during common setup, before networks are created. */
public final class ProcessingAdapters {
    private static final Map<String, Supplier<ProcessingAdapter>> FACTORIES = new LinkedHashMap<>();
    private static final Map<String, Supplier<ResourceProcessingAdapter>> RESOURCE_FACTORIES = new LinkedHashMap<>();
    private ProcessingAdapters() {}
    public static synchronized void registerResource(String id, Supplier<ResourceProcessingAdapter> factory) {
        if (RESOURCE_FACTORIES.putIfAbsent(id, Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate resource processing adapter " + id);
    }
    public static synchronized void register(String id, Supplier<ProcessingAdapter> factory) {
        if (FACTORIES.putIfAbsent(id, Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate processing adapter " + id);
    }
    static synchronized List<ResourceProcessingAdapter> createResourceAdapters() {
        List<ResourceProcessingAdapter> result = new ArrayList<>();
        for (var adapter : create()) result.add(new ItemProcessingBridge(adapter));
        result.add(new MekanismProcessingAdapter());
        NativeMachineBackends.create().forEach(backend -> result.add(new NativeMachineAdapter(backend)));
        RESOURCE_FACTORIES.forEach((id, factory) -> { try { result.add(factory.get()); } catch (RuntimeException failure) { CraftingService.LOGGER.error("Resource processing adapter {} could not initialize", id, failure); } });
        return result;
    }
    static synchronized List<ProcessingAdapter> create() {
        List<ProcessingAdapter> result = new ArrayList<>();
        result.add(new VanillaProcessingAdapter());
        result.add(new BrewingProcessingAdapter());
        result.add(new InventoryProcessingAdapter());
        FACTORIES.forEach((id, factory) -> { try { result.add(factory.get()); } catch (RuntimeException failure) { CraftingService.LOGGER.error("Processing adapter {} could not initialize", id, failure); } });
        return result;
    }
}
