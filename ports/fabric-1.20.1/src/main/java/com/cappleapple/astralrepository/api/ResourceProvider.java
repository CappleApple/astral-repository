package com.cappleapple.astralrepository.api;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Server-thread resource storage/transfer adapter. All amounts are non-negative native resource units. */
public interface ResourceProvider {
    String id();
    Object identity();
    ResourceLocation resourceType();
    Map<ResourceKey, Long> snapshot();
    /** Returns the amount accepted, never more than requested. Simulation does not mutate. */
    long insert(ResourceKey key, long amount, boolean simulate);
    /** Returns the amount extracted, never more than requested. Simulation does not mutate. */
    long extract(ResourceKey key, long amount, boolean simulate);
    long capacity();
    boolean valid();
    default long version() { return -1; }
    default String unit() { return "units"; }
    /** The client may map this ID to an installed visual adapter. */
    default ResourceLocation visualization() { return resourceType(); }
}