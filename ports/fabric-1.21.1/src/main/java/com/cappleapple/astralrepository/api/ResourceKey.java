package com.cappleapple.astralrepository.api;

import net.minecraft.resources.ResourceLocation;

/** Immutable identity. Implementations must include every component relevant to resource interchangeability. */
public interface ResourceKey {
    ResourceLocation type();
    ResourceLocation id();
}