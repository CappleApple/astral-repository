package com.cappleapple.astralrepository.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NativeSelfSeedSchedulerTest {
    private static CraftPlan<String> plan(long additional) {
        var growth = new CraftRecipe<>("native-growth", "seed", 1, "native", List.of(
                new CraftRecipe.Ingredient<>(List.of("seed"), 2), CraftRecipe.Ingredient.of("water")), 0, 1, 2);
        return new CraftPlanner<>(List.of(growth)).planAdditional("seed", additional,
                Map.of("seed", 2L, "water", additional), Set.of("native"));
    }

    private static CraftScheduler.Processor<String, String> result(long grossOutput, boolean chance) {
        return new CraftScheduler.Processor<>() {
            public boolean supports(String processor, CraftPlan.Node<String> node) { return true; }
            public CraftScheduler.Operation<String> start(String processor, CraftPlan.Node<String> node) {
                return new CraftScheduler.Operation<>() {
                    public Map<String, Long> poll() { return Map.of("seed", grossOutput); }
                    public Map<String, Long> cancel() { return node.inputs(); }
                    public boolean mayReturnShortOutput() { return chance; }
                };
            }
        };
    }

    @Test void repeatedPhysicalGrowthFinishesOnlyAfterTheRequestedNetIncrease() {
        var plan = plan(5);
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), result(3, false));
        for (int tick = 0; tick < 10 && !scheduler.terminal(); tick++) scheduler.tick(List.of("machine"), 1);
        assertEquals(CraftScheduler.State.COMPLETE, scheduler.state());
        assertEquals(5, scheduler.completed());
        var returned = scheduler.drain();
        assertEquals(7L, returned.get("seed"));
        assertEquals(5L, scheduler.targetCredit(returned));
        assertEquals(Map.of(), scheduler.drain());
    }

    @Test void returningOnlyTheOriginalSeedFailsAGuaranteedGrossYield() {
        var plan = plan(1);
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), result(2, false));
        scheduler.tick(List.of("machine"), 1);
        scheduler.tick(List.of("machine"), 1);
        assertEquals(CraftScheduler.State.FAILED, scheduler.state());
        assertEquals(0, scheduler.completed());
        var returned = scheduler.drain();
        assertEquals(Map.of("seed", 2L), returned);
        assertEquals(0L, scheduler.targetCredit(returned));
    }

    @Test void aShortNativeAttemptCannotCreditReturnedSeedTowardTheRetry() {
        var plan = plan(1);
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), result(2, true));
        scheduler.tick(List.of("machine"), 1);
        scheduler.tick(List.of("machine"), 1);
        assertTrue(scheduler.retryRequired());
        assertTrue(scheduler.terminal());
        assertNotEquals(CraftScheduler.State.COMPLETE, scheduler.state());
        var returned = scheduler.drain();
        assertEquals(Map.of("seed", 2L), returned);
        assertEquals(0L, scheduler.targetCredit(returned));
        assertEquals(1L, scheduler.targetCredit(Map.of("seed", 3L)));
    }

    @Test void cancellingBeforeNativeConsumptionRefundsStartupResourcesWithNoNewTargetCredit() {
        var plan = plan(1);
        var scheduler = new CraftScheduler<>(plan, plan.reservations(), new CraftScheduler.Processor<String, String>() {
            public boolean supports(String processor, CraftPlan.Node<String> node) { return true; }
            public CraftScheduler.Operation<String> start(String processor, CraftPlan.Node<String> node) {
                return new CraftScheduler.Operation<>() {
                    public Map<String, Long> poll() { return null; }
                    public Map<String, Long> cancel() { return node.inputs(); }
                    public Map<String, Long> recoverable() { return node.inputs(); }
                };
            }
        });
        scheduler.tick(List.of("machine"), 1);
        assertEquals(plan.reservations(), scheduler.recoverySnapshot());
        scheduler.cancel();
        assertEquals(CraftScheduler.State.CANCELLED, scheduler.state());
        var returned = scheduler.drain();
        assertEquals(plan.reservations(), returned);
        assertEquals(0L, scheduler.targetCredit(returned));
        assertEquals(Map.of(), scheduler.drain());
    }
}
