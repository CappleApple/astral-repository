package com.cappleapple.astralrepository.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;

public final class AstralTrims {
    public static final ResourceKey<TrimMaterial> MATERIAL = ResourceKey.create(Registries.TRIM_MATERIAL,
            Identifier.fromNamespaceAndPath(AstralContent.MOD_ID, "astral_gem"));
    public static boolean isAstral(ArmorTrim trim) { return trim != null && trim.material().is(MATERIAL); }
    private AstralTrims() {}
}
