package com.cappleapple.astralrepository.api;

import net.minecraft.resources.ResourceLocation;

public final class ResourceKinds {
    public static final ResourceLocation ITEM = new ResourceLocation("astral_repository:item");
    public static final ResourceLocation FLUID = new ResourceLocation("astral_repository:fluid");
    public static final ResourceLocation ENERGY = new ResourceLocation("astral_repository:energy");
    public static final ResourceLocation SOURCE = new ResourceLocation("astral_repository:source");
    public static final ResourceLocation STRESS = new ResourceLocation("astral_repository:stress");
    public static final ResourceKey FE = new ScalarKey(ENERGY, new ResourceLocation("neoforge:energy"));
    public static final ResourceKey ARS_SOURCE = new ScalarKey(SOURCE, new ResourceLocation("ars_nouveau:source"));
    private ResourceKinds() { }
    public record ScalarKey(ResourceLocation type, ResourceLocation id) implements ResourceKey { }
}