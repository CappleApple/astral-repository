package com.cappleapple.astralrepository.compat;

import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;

/** Complete item-only domains verified against the indicated optional release. */
public final class MoonlightRecipeCompatibility {
    private static final String IMPLEMENTATION="net.mehvahdjukaar.moonlight.api.resources.recipe.platform.BlockTypeSwapIngredientImpl";
    private static final boolean VERIFIED=ModList.get().getModContainerById("moonlight")
            .map(mod->mod.getModInfo().getVersion().toString().equals("1.21.1-3.7.2")).orElse(false);
    private MoonlightRecipeCompatibility() {}

    public static boolean isStableItemOnly(Ingredient ingredient){return isItemOnly(ingredient);}

    public static boolean isItemOnly(Ingredient ingredient){
        if(!VERIFIED)return false;
        var custom=ingredient.getCustomIngredient();
        // The exact implementation's test and getItems use the same cached item list.
        // Subclasses and other versions retain normal predicate matching.
        return custom!=null&&custom.getClass().getName().equals(IMPLEMENTATION);
    }
}
