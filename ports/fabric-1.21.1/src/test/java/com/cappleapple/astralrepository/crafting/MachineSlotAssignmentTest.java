package com.cappleapple.astralrepository.crafting;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineSlotAssignmentTest {
    @Test void backtracksWhenOnlyOneSlotAcceptsTheSecondIngredient() {
        assertEquals(List.of(1, 0), MachineSlotAssignment.assign(List.of(0, 1), List.of("copper", "tin"),
                (slot, item) -> slot == 0 || item.equals("copper")));
    }
    @Test void repeatedIngredientEntriesStillNeedDistinctSlots() {
        assertEquals(List.of(0, 1), MachineSlotAssignment.assign(List.of(0, 1), List.of("iron", "iron"), (slot, item) -> true));
    }
    @Test void cannotReuseARepeatedConfiguredSlot() {
        assertNull(MachineSlotAssignment.assign(List.of(0, 0), List.of("iron", "copper"), (slot, item) -> true));
    }
    @Test void rejectsPartialCapacityWithoutAssigningAnything() {
        assertNull(MachineSlotAssignment.assign(List.of(0, 1), List.of(3, 1), (slot, amount) -> amount <= 1));
    }
    @Test void preservesSelectedCountsAndOrder() {
        assertEquals(List.of(4, 2), MachineSlotAssignment.assign(List.of(2, 4), List.of(3, 1), (slot, count) -> slot == 4 || count == 1));
    }
    @Test void impossibleMatchingStopsWithinTheBudget() {
        int[] calls = {0};
        assertNull(MachineSlotAssignment.assign(java.util.stream.IntStream.range(0, 9).boxed().toList(),
                List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), (slot, item) -> { calls[0]++; return item != 8; }));
        assertTrue(calls[0] <= 4096);
    }
}
