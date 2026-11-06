package com.cappleapple.astralrepository.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Verify reservation and physical resource conservation for explicitly declared net-positive native cycles. */
class NativeSelfSeedPlannerTest {
    private static final Set<String> PROCESSES = Set.of("native", "crafting");

    private static CraftRecipe<String> grow(long seed, long net) {
        return new CraftRecipe<>("native-grow", "spore", net, "native", List.of(
                new CraftRecipe.Ingredient<>(List.of("spore"), seed),
                CraftRecipe.Ingredient.of("water")), 0, 1, seed);
    }

    private static CraftRecipe<String> ordinary(String id, String output, long count, String input, long inputCount) {
        return new CraftRecipe<>(id, output, count, "crafting", List.of(new CraftRecipe.Ingredient<>(List.of(input), inputCount)), 0, 1);
    }

    /** Execute the public topological plan using gross physical yields; no planner internals are inspected. */
    private static Map<String, Long> physicallyExecute(CraftPlan<String> plan) {
        Map<String, Long> held = new HashMap<>(plan.reservations());
        Set<Integer> finished = new HashSet<>();
        for (var node : plan.nodes()) {
            assertTrue(finished.containsAll(node.dependencies()), "A cycle must wait for the actual producer of its seed");
            for (var input : node.inputs().entrySet()) {
                long available = held.getOrDefault(input.getKey(), 0L);
                assertTrue(available >= input.getValue(), () -> "Plan spends unowned " + input.getKey() + ": " + held);
                held.put(input.getKey(), available - input.getValue());
            }
            held.merge(node.recipe().output(), Math.addExact(node.recipe().outputCount(), node.recipe().returnedSeedCount()), Math::addExact);
            finished.add(node.id());
        }
        return held;
    }

    @Test void oneSeedGrowsFiveAdditionalItemsWithoutReservingFiveSeeds() {
        var plan = new CraftPlanner<>(List.of(grow(1, 1))).planAdditional("spore", 5, Map.of("spore", 1L, "water", 5L), PROCESSES);
        assertEquals(Map.of("spore", 1L, "water", 5L), plan.reservations());
        assertEquals(5, plan.nodes().size());
        assertEquals(6L, physicallyExecute(plan).get("spore"));
        for (int index = 1; index < plan.nodes().size(); index++)
            assertTrue(plan.nodes().get(index).dependencies().contains(index - 1), "Returned seed requires a real preceding cycle");
    }

    @Test void aTwoSeedThreeResultCycleReservesTheTwoItemStartupOnce() {
        var plan = new CraftPlanner<>(List.of(grow(2, 1))).planAdditional("spore", 5, Map.of("spore", 2L, "water", 5L), PROCESSES);
        assertEquals(2L, plan.reservations().get("spore"));
        assertEquals(5, plan.nodes().size());
        assertEquals(7L, physicallyExecute(plan).get("spore"));
    }

    @Test void nativeOneToThreeGrowthRoundsByItsNetGain() {
        var plan = new CraftPlanner<>(List.of(grow(1, 2))).planAdditional("spore", 5, Map.of("spore", 1L, "water", 3L), PROCESSES);
        assertEquals(3, plan.nodes().size());
        assertEquals(Map.of("spore", 1L, "water", 3L), plan.reservations());
        assertEquals(7L, physicallyExecute(plan).get("spore"));
    }

    @Test void missingStartupSeedCannotBeObtainedByRecursingIntoItsOwnRecipe() {
        var planner = new CraftPlanner<>(List.of(grow(1, 1)));
        assertThrows(CraftPlanner.PlanningException.class,
                () -> planner.planAdditional("spore", 5, Map.of("water", 5L), PROCESSES));
        assertThrows(CraftPlanner.PlanningException.class,
                () -> new CraftPlanner<>(List.of(grow(2, 1))).planAdditional("spore", 1, Map.of("spore", 1L, "water", 1L), PROCESSES));
    }

    @Test void ordinaryFulfillmentCanReuseItsInitiallyCountedStockAsTheStartupSeed() {
        var plan = new CraftPlanner<>(List.of(grow(1, 1))).plan("spore", 5, Map.of("spore", 1L, "water", 4L), PROCESSES);
        assertEquals(4, plan.nodes().size());
        assertEquals(1L, plan.reservations().get("spore"));
        assertEquals(5L, physicallyExecute(plan).get("spore"));
    }

    @Test void returnedSeedDoesNotCountTowardAnAdditionalFinalRequest() {
        var plan = new CraftPlanner<>(List.of(grow(1, 1))).planAdditional("spore", 5, Map.of("spore", 100L, "water", 5L), PROCESSES);
        assertEquals(1L, plan.reservations().get("spore"));
        assertEquals(5, plan.nodes().size());
        long finalNetwork = 100 - plan.reservations().get("spore") + physicallyExecute(plan).get("spore");
        assertEquals(105L, finalNetwork);
    }

    @Test void netGrowthCanSupplyAnOrdinaryIntermediateIngredient() {
        var gift = ordinary("gift", "gift", 1, "spore", 6);
        var plan = new CraftPlanner<>(List.of(grow(1, 1), gift)).planAdditional("gift", 1, Map.of("spore", 1L, "water", 5L), PROCESSES);
        assertEquals(6, plan.nodes().size());
        assertEquals(Map.of("spore", 1L, "water", 5L), plan.reservations());
        var finalHeld = physicallyExecute(plan);
        assertEquals(1L, finalHeld.get("gift"));
        assertEquals(0L, finalHeld.get("spore"));
    }

    @Test void selfSeedPermissionDoesNotPermitAConversionCycleThroughAnotherIngredient() {
        var growThroughSeed = new CraftRecipe<>("bad-side-cycle", "spore", 1, "native", List.of(
                CraftRecipe.Ingredient.of("spore"), CraftRecipe.Ingredient.of("water")), 0, 1, 1);
        var seedToWater = ordinary("seed-to-water", "water", 1, "spore", 1);
        assertThrows(CraftPlanner.PlanningException.class, () -> new CraftPlanner<>(List.of(growThroughSeed, seedToWater))
                .planAdditional("spore", 1, Map.of("spore", 2L), PROCESSES));
    }

    @Test void ordinaryCompressionDecompressionStillCannotSpendAncestorStock() {
        var recipes = List.of(grow(1, 1), ordinary("compress", "block", 1, "ingot", 9), ordinary("unpack", "ingot", 9, "block", 1));
        assertThrows(CraftPlanner.PlanningException.class, () -> new CraftPlanner<>(recipes)
                .planAdditional("ingot", 1, Map.of("ingot", 9L, "spore", 1L, "water", 1L), PROCESSES));
    }

    @Test void ordinarySelfReferentialRecipesRemainRejectedWithoutTheNativeDeclaration() {
        var ordinarySelf = ordinary("untrusted-cycle", "spore", 2, "spore", 1);
        assertThrows(CraftPlanner.PlanningException.class, () -> new CraftPlanner<>(List.of(ordinarySelf))
                .planAdditional("spore", 1, Map.of("spore", 1L), PROCESSES));
    }

    @Test void shortagePreviewRequiresTheStartupSeedOnlyOnce() {
        var preview = new MissingIngredients<>(List.of(grow(2, 1)));
        assertEquals(List.of(new MissingIngredients.Missing<>(List.of("spore"), "", 2)),
                preview.find("spore", 5, Map.of("water", 5L), PROCESSES));
        assertEquals(List.of(new MissingIngredients.Missing<>(List.of("water"), "", 2)),
                preview.find("spore", 5, Map.of("spore", 2L, "water", 3L), PROCESSES));
    }
}
