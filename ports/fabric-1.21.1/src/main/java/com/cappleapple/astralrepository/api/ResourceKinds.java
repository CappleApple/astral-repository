package com.cappleapple.astralrepository.api;

import net.minecraft.resources.ResourceLocation;

public final class ResourceKinds {
    public static final ResourceLocation CHEMICAL = ResourceLocation.parse("astral_repository:chemical");
    public static final ResourceLocation GAS = ResourceLocation.parse("astral_repository:gas");
    public static final ResourceLocation INFUSION = ResourceLocation.parse("astral_repository:infusion");
    public static final ResourceLocation PIGMENT = ResourceLocation.parse("astral_repository:pigment");
    public static final ResourceLocation SLURRY = ResourceLocation.parse("astral_repository:slurry");
    public static final ResourceLocation ITEM = ResourceLocation.parse("astral_repository:item");
    public static final ResourceLocation FLUID = ResourceLocation.parse("astral_repository:fluid");
    public static final ResourceLocation ENERGY = ResourceLocation.parse("astral_repository:energy");
    public static final ResourceLocation SOURCE = ResourceLocation.parse("astral_repository:source");
    public static final ResourceLocation STRESS = ResourceLocation.parse("astral_repository:stress");
    public static final ResourceKey FE = new ScalarKey(ENERGY, ResourceLocation.parse("neoforge:energy"));
    public static final ResourceKey ARS_SOURCE = new ScalarKey(SOURCE, ResourceLocation.parse("ars_nouveau:source"));
    private ResourceKinds() { }
    public record ScalarKey(ResourceLocation type, ResourceLocation id) implements ResourceKey { }
}