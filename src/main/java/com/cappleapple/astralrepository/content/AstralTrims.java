package com.cappleapple.astralrepository.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;

public final class AstralTrims {
    public static final ResourceKey<TrimMaterial> MATERIAL = ResourceKey.create(Registries.TRIM_MATERIAL,
            new ResourceLocation(AstralContent.MOD_ID, "astral_gem"));
    public static boolean isAstral(ArmorTrim trim) { return trim != null && trim.material().is(MATERIAL); }
    private AstralTrims() {}
}
