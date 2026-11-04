package com.cappleapple.astralrepository.crafting;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftingCoreTest {
    private static CraftRecipe<String> recipe(String id, String output, long count, String process, String... inputs) {
        return new CraftRecipe<>(id, output, count, process, Arrays.stream(inputs).map(CraftRecipe.Ingredient::of).toList(), 0, 2);
    }
    @Test void additionalRequestsProduceTheEntireCountWithoutReservingExistingOutputs() {
        var planner=new CraftPlanner<>(List.of(recipe("planks","plank",4,"table","log")));
        var plan=planner.planAdditional("plank",64,Map.of("plank",64L,"log",16L),Set.of("table"));
        assertEquals(16,plan.nodes().size());
        assertEquals(Map.of("log",16L),plan.reservations());
        var scheduler=new CraftScheduler<>(plan,plan.reservations(),new InstantProcessors());
        for(int i=0;i<20;i++)scheduler.tick(List.of("table1","table2"),2);
        assertEquals(CraftScheduler.State.COMPLETE,scheduler.state());
        assertEquals(Map.of("plank",64L),scheduler.drain());
        assertThrows(CraftPlanner.PlanningException.class,()->planner.planAdditional("plank",1,Map.of("plank",64L),Set.of("table")));
    }
    @Test void existingTargetStockCannotBootstrapASelfRecipe() {
        var recipes = List.of(recipe("grow", "seed", 2, "table", "seed", "soil"));
        assertThrows(CraftPlanner.PlanningException.class, () -> new CraftPlanner<>(recipes)
                .planAdditional("seed", 2, Map.of("seed", 1L, "soil", 1L), Set.of("table")));
    }
    private static List<CraftRecipe<String>> ironRecipes() {
        return List.of(recipe("unpack", "ingot", 9, "table", "block"),
                new CraftRecipe<>("pack", "block", 1, "table",
                        List.of(new CraftRecipe.Ingredient<>(List.of("ingot"), 9)), 0, 2),
                new CraftRecipe<>("smelt", "ingot", 1, "furnace",
                        List.of(CraftRecipe.Ingredient.of("ore")), 0, 200));
    }
    @Test void compressionRoundTripsCannotCountStoredTargetsAsNewOutput() {
        var planner = new CraftPlanner<>(ironRecipes());
        assertThrows(CraftPlanner.PlanningException.class, () -> planner.planAdditional(
                "ingot", 9, Map.of("ingot", 9L), Set.of("table", "furnace")));
        assertThrows(CraftPlanner.PlanningException.class, () -> planner.planAdditional(
                "block", 1, Map.of("block", 1L), Set.of("table", "furnace")));
    }
    @Test void usesStoredBlocksBeforeSmeltingAndProducesTheRequestedNetIncrease() {
        var stock = Map.of("ingot", 64L, "block", 2L, "ore", 3L);
        var plan = new CraftPlanner<>(ironRecipes()).planAdditional("ingot", 21, stock, Set.of("table", "furnace"));
        assertEquals(List.of("unpack", "unpack", "smelt", "smelt", "smelt"),
                plan.nodes().stream().map(node -> node.recipe().id()).toList());
        assertEquals(Map.of("block", 2L, "ore", 3L), plan.reservations());
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), new InstantProcessors());
        for (int i = 0; i < 8; i++) scheduler.tick(List.of("table", "furnace"), 2);
        assertEquals(CraftScheduler.State.COMPLETE, scheduler.state());
        assertEquals(Map.of("ingot", 21L), scheduler.drain());
        assertEquals(64L, stock.get("ingot"));
    }
    @Test void maximumRequestUsesAStoredPrefixWithoutRecursingForEachOperation() {
        var plan = new CraftPlanner<>(ironRecipes()).planAdditional("ingot", 4096,
                Map.of("block", 454L, "ore", 10L), Set.of("table", "furnace"));
        assertEquals(464, plan.nodes().size());
        assertEquals(Map.of("block", 454L, "ore", 10L), plan.reservations());
        var simple = new CraftPlanner<>(List.of(recipe("simple", "output", 1, "table", "input")))
                .planAdditional("output", 4096, Map.of("input", 4096L), Set.of("table"));
        assertEquals(4096, simple.nodes().size());
        assertEquals(Map.of("input", 4096L), simple.reservations());
    }
    @Test void mixedProductionPreservesDependenciesForALaterConsumer() {
        var recipes = new ArrayList<>(ironRecipes());
        recipes.add(new CraftRecipe<>("final", "final", 1, "table",
                List.of(new CraftRecipe.Ingredient<>(List.of("ingot"), 21)), 0, 2));
        var plan = new CraftPlanner<>(recipes).plan("final", 1, Map.of("block", 2L, "ore", 3L), Set.of("table", "furnace"));
        assertEquals(Set.of(0, 1, 2, 3, 4), plan.nodes().get(5).dependencies());
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), new InstantProcessors());
        for (int i = 0; i < 10; i++) scheduler.tick(List.of("table", "furnace"), 2);
        assertEquals(CraftScheduler.State.COMPLETE, scheduler.state());
        assertEquals(Map.of("final", 1L), scheduler.drain());
    }
    @Test void backtracksTheMixedPrefixWhenALaterInputNeedsABlock() {
        var recipes = new ArrayList<>(ironRecipes());
        recipes.add(new CraftRecipe<>("final", "final", 1, "table", List.of(
                new CraftRecipe.Ingredient<>(List.of("ingot"), 20), CraftRecipe.Ingredient.of("block")), 0, 2));
        var plan = new CraftPlanner<>(recipes).plan("final", 1, Map.of("block", 2L, "ore", 11L), Set.of("table", "furnace"));
        assertEquals(1, plan.nodes().stream().filter(node -> node.recipe().id().equals("unpack")).count());
        assertEquals(11, plan.nodes().stream().filter(node -> node.recipe().id().equals("smelt")).count());
        assertEquals(Map.of("block", 2L, "ore", 11L), plan.reservations());
    }
    @Test void resolvesFifteenStepsWithOnlyTheFinalProductRequested() {
        List<CraftRecipe<String>> recipes = new ArrayList<>();
        for (int i = 1; i <= 15; i++) recipes.add(recipe("step_" + i, "step_" + i, 1, "table", i == 1 ? "raw" : "step_" + (i - 1)));
        CraftPlan<String> plan = new CraftPlanner<>(recipes).plan("step_15", 1, Map.of("raw", 1L), Set.of("table"));
        assertEquals(15, plan.nodes().size());
        assertEquals(Map.of("raw", 1L), plan.reservations());
        for (int i = 1; i < 15; i++) assertEquals(Set.of(i - 1), plan.nodes().get(i).dependencies());
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), new InstantProcessors());
        for (int i = 0; i < 16; i++) scheduler.tick(List.of("table1", "table2"), 2);
        assertEquals(CraftScheduler.State.COMPLETE, scheduler.state());
        assertEquals(Map.of("step_15", 1L), scheduler.drain());
    }
    @Test void rejectsCyclesAndFindsAnAcyclicAlternative() {
        var cycle = List.of(recipe("a_cycle", "a", 1, "table", "b"), recipe("b_cycle", "b", 1, "table", "a"));
        assertThrows(CraftPlanner.PlanningException.class, () -> new CraftPlanner<>(cycle).plan("a", 1, Map.of(), Set.of("table")));
        List<CraftRecipe<String>> viable = new ArrayList<>(cycle);
        viable.add(recipe("z_from_raw", "a", 1, "table", "raw"));
        var plan = new CraftPlanner<>(viable).plan("a", 1, Map.of("raw", 1L), Set.of("table"));
        assertEquals(1, plan.nodes().size());
        assertEquals("z_from_raw", plan.nodes().getFirst().recipe().id());
    }
    @Test void failedBranchesNeverLeakReservations() {
        var expensive = new CraftRecipe<>("preferred_but_missing", "final", 1L, "table", List.of(CraftRecipe.Ingredient.of("iron"), CraftRecipe.Ingredient.of("absent")), 10, 1);
        var alternative = recipe("alternative", "final", 1, "table", "iron");
        Map<String, Long> inventory = new HashMap<>(Map.of("iron", 1L));
        var plan = new CraftPlanner<>(List.of(expensive, alternative)).plan("final", 1, inventory, Set.of("table"));
        assertEquals(Map.of("iron", 1L), plan.reservations());
        assertEquals(Map.of("iron", 1L), inventory);
        assertEquals(1, plan.nodes().size());
    }
    @Test void backtracksAnEarlierRecipeWhenAnotherBranchNeedsItsMaterial() {
        var recipes = List.of(recipe("final", "final", 1, "table", "a", "b"),
                recipe("a_iron", "a", 1, "table", "iron"), recipe("a_wood", "a", 1, "table", "wood"),
                recipe("b", "b", 1, "table", "iron"));
        var plan = new CraftPlanner<>(recipes).plan("final", 1, Map.of("iron", 1L, "wood", 1L), Set.of("table"));
        assertEquals(List.of("a_wood", "b", "final"), plan.nodes().stream().map(node -> node.recipe().id()).toList());
        assertEquals(Map.of("iron", 1L, "wood", 1L), plan.reservations());
    }
    @Test void backtracksIngredientAlternativesAcrossBranches() {
        var alternatives = new CraftRecipe<>("a", "a", 1L, "table", List.of(new CraftRecipe.Ingredient<>(List.of("iron", "wood"), 1)), 0, 1);
        var plan = new CraftPlanner<>(List.of(alternatives, recipe("final", "final", 1, "table", "a", "iron")))
                .plan("final", 1, Map.of("iron", 1L, "wood", 1L), Set.of("table"));
        assertEquals("wood", plan.nodes().getFirst().selected().getFirst().key());
    }
    @Test void sharedIntermediateSurplusFeedsBothBranches() {
        var recipes = List.of(recipe("planks", "plank", 4, "table", "log"), recipe("a", "a", 1, "table", "plank", "plank"),
                recipe("b", "b", 1, "table", "plank", "plank"), recipe("final", "final", 1, "table", "a", "b"));
        var plan = new CraftPlanner<>(recipes).plan("final", 1, Map.of("log", 1L), Set.of("table"));
        assertEquals(4, plan.nodes().size());
        assertEquals(Map.of("log", 1L), plan.reservations());
        assertEquals(Set.of(0), plan.nodes().get(1).dependencies());
        assertEquals(Set.of(0), plan.nodes().get(2).dependencies());
    }
    @Test void rejectsUnavailableProcessorAndHandlesExistingFinalStock() {
        var planner = new CraftPlanner<>(List.of(recipe("iron", "iron", 1, "furnace", "ore")));
        assertThrows(CraftPlanner.PlanningException.class, () -> planner.plan("iron", 1, Map.of("ore", 1L), Set.of("table")));
        var stock = planner.plan("iron", 2, Map.of("iron", 2L), Set.of());
        assertTrue(stock.nodes().isEmpty());
        assertEquals(Map.of("iron", 2L), stock.reservations());
    }
    @Test void reservationLedgerRejectsConcurrentDoubleClaimsAndReleasesIdempotently() {
        var ledger = new ReservationLedger<String>();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Map<String, Long> stock = Map.of("iron", 4L);
        assertTrue(ledger.reserve(first, Map.of("iron", 3L), stock));
        assertFalse(ledger.reserve(second, Map.of("iron", 2L), stock));
        assertEquals(Map.of("iron", 1L), ledger.available(stock));
        assertEquals(Map.of("iron", 3L), ledger.release(first));
        assertEquals(Map.of(), ledger.release(first));
        assertTrue(ledger.reserve(second, Map.of("iron", 4L), stock));
    }
    @Test void eightFurnacesProcessEightIndependentOperationsTogether() {
        var plan = new CraftPlanner<>(List.of(recipe("smelt", "ingot", 1, "furnace", "ore")))
                .plan("ingot", 8, Map.of("ore", 8L), Set.of("furnace"));
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), new InstantProcessors());
        List<String> furnaces = java.util.stream.IntStream.range(0, 8).mapToObj(i -> "furnace" + i).toList();
        scheduler.tick(furnaces, 8);
        assertEquals(8, scheduler.active());
        scheduler.tick(furnaces, 8);
        assertEquals(CraftScheduler.State.COMPLETE, scheduler.state());
        assertEquals(Map.of("ingot", 8L), scheduler.drain());
    }
    @Test void cancellationReturnsCompletedIntermediatesAndNeverRecreatesConsumedInputs() {
        var plan = new CraftPlanner<>(List.of(recipe("smelt", "ingot", 1, "furnace", "ore"), recipe("plate", "plate", 1, "table", "ingot")))
                .plan("plate", 1, Map.of("ore", 1L), Set.of("furnace", "table"));
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), new InstantProcessors());
        scheduler.tick(List.of("furnace"), 1);
        scheduler.tick(List.of("furnace"), 1);
        assertEquals(1, scheduler.completed());
        scheduler.cancel();
        assertEquals(Map.of("ingot", 1L), scheduler.drain());
        assertEquals(Map.of(), scheduler.drain());
    }
    @Test void cancellationWhileProcessingRefundsOnlyOwnedResources() {
        var plan = new CraftPlanner<>(List.of(recipe("smelt", "ingot", 1, "furnace", "ore")))
                .plan("ingot", 8, Map.of("ore", 8L), Set.of("furnace"));
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), new InstantProcessors());
        scheduler.tick(List.of("furnace1", "furnace2"), 2);
        assertEquals(Map.of("ore", 8L), scheduler.recoverySnapshot());
        scheduler.cancel();
        assertEquals(CraftScheduler.State.CANCELLED, scheduler.state());
        assertEquals(Map.of("ore", 8L), scheduler.drain());
    }
    @Test void failedProcessorCancelsOtherBranchesAndPreservesTheirRefunds() {
        var plan = new CraftPlanner<>(List.of(recipe("smelt", "ingot", 1, "furnace", "ore")))
                .plan("ingot", 2, Map.of("ore", 2L), Set.of("furnace"));
        var driver = new CraftScheduler.Processor<String, String>() {
            public boolean supports(String processor, CraftPlan.Node<String> node) { return true; }
            public CraftScheduler.Operation<String> start(String processor, CraftPlan.Node<String> node) {
                return new CraftScheduler.Operation<>() {
                    public Map<String, Long> poll() { throw new IllegalStateException("Machine failed"); }
                    public Map<String, Long> cancel() { return node.inputs(); }
                };
            }
        };
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), driver);
        scheduler.tick(List.of("one", "two"), 2);
        scheduler.tick(List.of("one", "two"), 2);
        assertEquals(CraftScheduler.State.FAILED, scheduler.state());
        assertEquals(0, scheduler.active());
        assertEquals(Map.of("ore", 2L), scheduler.drain());
    }
    @Test void corruptedProcessorOutputIsReturnedButNeverReportedAsSuccessful() {
        var plan = new CraftPlanner<>(List.of(recipe("smelt", "ingot", 1, "furnace", "ore")))
                .plan("ingot", 1, Map.of("ore", 1L), Set.of("furnace"));
        var driver = new CraftScheduler.Processor<String, String>() {
            public boolean supports(String processor, CraftPlan.Node<String> node) { return true; }
            public CraftScheduler.Operation<String> start(String processor, CraftPlan.Node<String> node) {
                return new CraftScheduler.Operation<>() {
                    public Map<String, Long> poll() { return Map.of("slag", 1L); }
                    public Map<String, Long> cancel() { return Map.of(); }
                };
            }
        };
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), driver);
        scheduler.tick(List.of("furnace"), 1); scheduler.tick(List.of("furnace"), 1);
        assertEquals(CraftScheduler.State.FAILED, scheduler.state());
        assertEquals(Map.of("slag", 1L), scheduler.drain());
    }
    @Test void searchLimitsReportFailureInsteadOfRunningUnbounded() {
        var recipe = recipe("a", "a", 1, "table", "raw");
        assertThrows(CraftPlanner.PlanningException.class, () -> new CraftPlanner<>(List.of(recipe), 4, 2, 100).plan("a", 5, Map.of("raw", 5L), Set.of("table")));
    }
    private static final class InstantProcessors implements CraftScheduler.Processor<String, String> {
        public boolean supports(String processor, CraftPlan.Node<String> node) { return processor.startsWith(node.recipe().process()); }
        public CraftScheduler.Operation<String> start(String processor, CraftPlan.Node<String> node) {
            return new CraftScheduler.Operation<>() {
                boolean complete;
                public Map<String, Long> poll() { complete = true; return Map.of(node.recipe().output(), node.recipe().outputCount()); }
                public Map<String, Long> cancel() { return complete ? Map.of() : node.inputs(); }
                public Map<String, Long> recoverable() { return complete ? Map.of() : node.inputs(); }
            };
        }
    }
}
