package com.cappleapple.astralrepository.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeProcessLabelsTest {
    @Test void sharedOutputsStillIdentifyTheirDistinctProcesses() {
        assertEquals("Smelting / Furnace", RecipeProcessLabels.describe("minecraft:smelting"));
        assertEquals("Blasting / Blast furnace", RecipeProcessLabels.describe("minecraft:blasting"));
        assertEquals("Create / Crushing wheels", RecipeProcessLabels.describe("create:crushing"));
        assertEquals("Create / Millstone", RecipeProcessLabels.describe("create:milling"));
        assertEquals("Immersive Engineering / Alloy Kiln", RecipeProcessLabels.describe("immersiveengineering:alloy"));
        assertEquals("Oritech / Foundry", RecipeProcessLabels.describe("oritech:foundry"));
    }

    @Test void vanillaWorkstationsHaveConcreteNames() {
        assertEquals("Crafting table", RecipeProcessLabels.describe("minecraft:crafting"));
        assertEquals("Brewing stand", RecipeProcessLabels.describe("minecraft:brewing"));
        assertEquals("Stonecutter", RecipeProcessLabels.describe("minecraft:stonecutting"));
    }

    @Test void unfamiliarModsRetainTheirNamespaceAndProcessInsteadOfGuessingAMachine() {
        assertEquals("Example Mod / Alloy Processing", RecipeProcessLabels.describe("example_mod:alloy_processing"));
        assertEquals("Other / Casting Basin", RecipeProcessLabels.describe("other:casting/basin"));
        assertNotEquals(RecipeProcessLabels.describe("first:smelting"), RecipeProcessLabels.describe("second:smelting"));
    }
}
