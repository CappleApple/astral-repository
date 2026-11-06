package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreateSequencedRecipesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private record Output(ItemStack stack, float weight) {
        public ItemStack getStack() { return stack; }
        public float getChance() { return weight; }
    }
    private static Map<ResourceKey, Long> quantities(Object pool, String method) {
        @SuppressWarnings("unchecked") var result = (Map<ResourceKey, Long>)NativeBackendReflection.call(pool, method);
        return result;
    }
    @Test void finalResultsAreMutuallyExclusiveWeightedChoices() {
        var iron = new ItemKey(new ItemStack(Items.IRON_INGOT)); var gold = new ItemKey(new ItemStack(Items.GOLD_INGOT));
        Object pool = NativeBackendReflection.call(CreateSequencedRecipes.class, "pool", List.of(new Output(iron.sample().copyWithCount(3), 8), new Output(gold.sample(), 2)));
        assertTrue(quantities(pool, "guaranteed").isEmpty());
        assertEquals(Map.of(iron, 3L, gold, 1L), quantities(pool, "possible"));
    }
    @Test void duplicateIdentitiesKeepMinimumGuaranteeAndMaximumPossibleCount() {
        var iron = new ItemKey(new ItemStack(Items.IRON_INGOT));
        Object pool = NativeBackendReflection.call(CreateSequencedRecipes.class, "pool", List.of(new Output(iron.sample().copyWithCount(2), 1), new Output(iron.sample().copyWithCount(5), 1)));
        assertEquals(Map.of(iron, 2L), quantities(pool, "guaranteed"));
        assertEquals(Map.of(iron, 5L), quantities(pool, "possible"));
        Object nullable = NativeBackendReflection.call(CreateSequencedRecipes.class, "pool", List.of(new Output(iron.sample(), 1), new Output(ItemStack.EMPTY, 1)));
        assertTrue(quantities(nullable, "guaranteed").isEmpty());
    }
    @Test void invalidWeightsCannotBecomeRecipeKnowledge() {
        assertThrows(IllegalArgumentException.class, () -> NativeBackendReflection.call(CreateSequencedRecipes.class, "pool", List.of(new Output(new ItemStack(Items.IRON_INGOT), Float.NaN))));
        assertThrows(IllegalArgumentException.class, () -> NativeBackendReflection.call(CreateSequencedRecipes.class, "pool", List.of(new Output(new ItemStack(Items.IRON_INGOT), 0))));
    }
    @Test void discoveryNeverPreviewsTheFinalNativeRoll() {
        for (int index : List.of(0, 3, 4)) {
            var failure = assertThrows(IllegalArgumentException.class, () -> NativeBackendReflection.call(CreateSequencedRecipes.class, "progress", null,
                    new ItemStack(Items.PAPER), ResourceLocation.withDefaultNamespace("fixture"), index, 3));
            assertTrue(failure.getMessage().contains("nonfinal"));
        }
    }
}
