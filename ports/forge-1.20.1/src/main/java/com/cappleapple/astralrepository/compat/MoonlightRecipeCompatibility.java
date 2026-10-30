package com.cappleapple.astralrepository.compat;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fml.ModList;

/** Complete item-only domains verified against the indicated optional release. */
public final class MoonlightRecipeCompatibility {
    private static final String IMPLEMENTATION="net.mehvahdjukaar.moonlight.api.resources.recipe.forge.BlockTypeSwapIngredientImpl";
    private static final boolean VERIFIED=ModList.get().getModContainerById("moonlight")
            .map(mod->mod.getModInfo().getVersion().toString().equals("1.20-2.16.38B")).orElse(false);
    private MoonlightRecipeCompatibility() {}

    // Forge invalidation can rebuild this domain without replacing the recipe object.
    public static boolean isStableItemOnly(Ingredient ingredient){return false;}

    public static boolean isItemOnly(Ingredient ingredient){
        if(!VERIFIED)return false;
        // The exact implementation inherits the vanilla item-only test over getItems().
        return ingredient.getClass().getName().equals(IMPLEMENTATION);
    }
}
