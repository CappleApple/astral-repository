package com.cappleapple.astralrepository.port;

import java.util.Map;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeCompatTest {
    @BeforeAll static void bootstrap(){com.cappleapple.astralrepository.MinecraftTestBootstrap.initialize();}
    @Test void shapedRecipePreservesInternalEmptySlotsWithoutConstructingEmptyIngredients(){
        var recipe=new ShapedRecipe(new Recipe.CommonInfo(true),new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC,""),
            ShapedRecipePattern.of(Map.of('#',Ingredient.of(Items.IRON_INGOT)),"# #"," # "),new ItemStackTemplate(Items.IRON_TRAPDOOR));
        var slots=RecipeCompat.ingredients(recipe);
        assertEquals(6,slots.size());
        for(int index=0;index<slots.size();index++)assertEquals(index==0||index==2||index==4,slots.get(index).isPresent(),"recipe slot "+index);
        assertEquals(3,slots.stream().flatMap(java.util.Optional::stream).count());
    }
}
