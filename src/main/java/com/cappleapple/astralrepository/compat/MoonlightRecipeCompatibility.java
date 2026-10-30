package com.cappleapple.astralrepository.compat;

import net.minecraft.world.item.crafting.Ingredient;
import net.fabricmc.loader.api.FabricLoader;

/** Complete item-only domains verified against the indicated optional release. */
public final class MoonlightRecipeCompatibility {
    private static final String IMPLEMENTATION="net.mehvahdjukaar.moonlight.api.resources.recipe.fabric.BlockTypeSwapIngredientImpl";
    private static final boolean VERIFIED=FabricLoader.getInstance().getModContainer("moonlight")
            .map(mod->mod.getMetadata().getVersion().getFriendlyString().equals("1.20-2.16.38B")).orElse(false);
    private MoonlightRecipeCompatibility() {}

    public static boolean isStableItemOnly(Ingredient ingredient){return isItemOnly(ingredient);}

    public static boolean isItemOnly(Ingredient ingredient){
        if(!VERIFIED)return false;
        // The exact implementation tests precisely its cached matching item list.
        var custom=((net.fabricmc.fabric.api.recipe.v1.ingredient.FabricIngredient)(Object)ingredient).getCustomIngredient();
        return custom!=null&&custom.getClass().getName().equals(IMPLEMENTATION);
    }
}
