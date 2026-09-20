package com.cappleapple.astralrepository.api;

import net.minecraft.resources.Identifier;

/** Immutable identity. Implementations must include every component relevant to resource interchangeability. */
public interface ResourceKey {
    Identifier type();
    Identifier id();
}