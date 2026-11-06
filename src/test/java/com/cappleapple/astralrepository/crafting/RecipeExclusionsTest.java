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
    @Test void eitherActualSourceBlocksACompositeProfileWithoutEncodingIdsInItsAlias() {
        String alias="astral_repository:native/industrialforegoing/host-profile/industrialforegoing:intrinsic/stonework";
        var sources=Set.of("minecraft:stone", "industrialforegoing:stonework_generate/diorite");
        assertTrue(RecipeExclusions.allows(alias,sources,Set.of()));
        for(String source:sources)assertFalse(RecipeExclusions.allows(alias,sources,Set.of(source)),source);
        assertFalse(RecipeExclusions.allows(alias,sources,Set.of(alias)));
        assertTrue(RecipeExclusions.allows(alias,sources,Set.of("other:stone","minecraft:stone_extra")));
    }
    @Test void blockedCompositeSourceKeepsTheIndependentFeasibleNativePath() throws Exception {
        var composite=new CraftRecipe<>("astral_repository:native/composite","target",1,"native",List.of(CraftRecipe.Ingredient.of("lava")),0,1);
        var independent=new CraftRecipe<>("astral_repository:native/independent","target",1,"native",List.of(CraftRecipe.Ingredient.of("ore")),0,2);
        var dependencies=Map.of(composite.id(),Set.of("pack:generate","pack:smelt"),independent.id(),Set.of("pack:independent"));
        for(String blocked:dependencies.get(composite.id())) {
            var allowed=List.of(composite,independent).stream().filter(recipe->RecipeExclusions.allows(recipe.id(),dependencies.get(recipe.id()),Set.of(blocked))).toList();
            var result=new AsyncCraftPlanner<>(allowed).submit("target",1,Map.of("lava",1L,"ore",1L),Set.of("native")).get(5,TimeUnit.SECONDS);
            assertNotNull(result.plan(),result.error());assertEquals(Map.of("ore",1L),result.plan().reservations());
            assertEquals(independent.id(),result.plan().nodes().get(0).recipe().id());
        }
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
