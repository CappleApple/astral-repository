package com.cappleapple.astralrepository.api;

import net.minecraft.resources.Identifier;

public final class ResourceKinds {
    public static final Identifier CHEMICAL = Identifier.parse("astral_repository:chemical");
    public static final Identifier GAS = Identifier.parse("astral_repository:gas");
    public static final Identifier INFUSION = Identifier.parse("astral_repository:infusion");
    public static final Identifier PIGMENT = Identifier.parse("astral_repository:pigment");
    public static final Identifier SLURRY = Identifier.parse("astral_repository:slurry");
    public static final Identifier ITEM = Identifier.parse("astral_repository:item");
    public static final Identifier FLUID = Identifier.parse("astral_repository:fluid");
    public static final Identifier ENERGY = Identifier.parse("astral_repository:energy");
    public static final Identifier SOURCE = Identifier.parse("astral_repository:source");
    public static final Identifier STRESS = Identifier.parse("astral_repository:stress");
    public static final ResourceKey FE = new ScalarKey(ENERGY, Identifier.parse("neoforge:energy"));
    public static final ResourceKey ARS_SOURCE = new ScalarKey(SOURCE, Identifier.parse("ars_nouveau:source"));
    private ResourceKinds() { }
    public record ScalarKey(Identifier type, Identifier id) implements ResourceKey { }
}