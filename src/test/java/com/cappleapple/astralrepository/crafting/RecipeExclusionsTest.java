package com.cappleapple.astralrepository.crafting;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeExclusionsTest {
    @Test void blocksExactAndAdapterWrappedRecipesOnly() {
        var excluded=Set.of("minecraft:iron_ingot_from_iron_block");
        assertFalse(RecipeExclusions.allows("minecraft:iron_ingot_from_iron_block",excluded));
        assertFalse(RecipeExclusions.allows("pack:press/minecraft:iron_ingot_from_iron_block",excluded));
        assertTrue(RecipeExclusions.allows("minecraft:iron_ingot_from_smelting_raw_iron",excluded));
        assertTrue(RecipeExclusions.allows("other:iron_ingot_from_iron_block",excluded));
        assertTrue(RecipeExclusions.allows("minecraft:iron_ingot_from_iron_block_extra",excluded));
    }
    @Test void filtersIntermediateRecipesAndKeepsAnAlternativePath() throws Exception {
        var unpack=new CraftRecipe<>("minecraft:iron_ingot_from_iron_block","ingot",9,"crafting",List.of(CraftRecipe.Ingredient.of("block")),0,1);
        var smelt=new CraftRecipe<>("minecraft:smelting","ingot",1,"smelting",List.of(CraftRecipe.Ingredient.of("ore")),0,200);
        var target=new CraftRecipe<>("minecraft:target","target",1,"crafting",List.of(CraftRecipe.Ingredient.of("ingot")),0,1);
        var excluded=Set.of(unpack.id());
        var recipes=List.of(unpack,smelt,target).stream().filter(recipe->RecipeExclusions.allows(recipe.id(),excluded)).toList();
        var result=new AsyncCraftPlanner<>(recipes).submit("target",1,Map.of("block",1L,"ore",1L),Set.of("crafting","smelting")).get(5,TimeUnit.SECONDS);
        assertNotNull(result.plan(),result.error());
        assertEquals(Map.of("ore",1L),result.plan().reservations());
        assertTrue(result.plan().nodes().stream().noneMatch(node->node.recipe().id().equals(unpack.id())));
    }
}
