package com.cappleapple.astralrepository.menu;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeTransferPlanTest {
    @Test void reassignsSharedAlternativeForAnExactIngredient() {
        assertEquals(List.of("birch", "oak"), RecipeTransferPlan.assign(List.of(List.of("oak", "birch"), List.of("oak")), Map.of("oak", 1L, "birch", 1L), 1, k -> 64));
    }
    @Test void neverCountsOneStackTwice() {
        assertNull(RecipeTransferPlan.assign(List.of(List.of("iron"), List.of("iron")), Map.of("iron", 1L), 1, k -> 64));
    }
    @Test void wholeBatchUsesOneIdentityPerSlot() {
        assertNull(RecipeTransferPlan.assign(List.of(List.of("oak", "birch")), Map.of("oak", 2L, "birch", 2L), 4, k -> 64));
        assertEquals(List.of("birch", "oak"), RecipeTransferPlan.assign(List.of(List.of("oak", "birch"), List.of("oak")), Map.of("oak", 4L, "birch", 4L), 4, k -> 64));
    }
    @Test void retainsShapedHolesAndEnforcesStackLimits() {
        var result = RecipeTransferPlan.assign(List.of(List.of("tool"), List.<String>of()), Map.of("tool", 100L), 1, k -> 1);
        assertEquals(Arrays.asList("tool", null), result);
        assertNull(RecipeTransferPlan.assign(List.of(List.of("tool")), Map.of("tool", 100L), 2, k -> 1));
    }
    @Test void capsRequestsAndGridSize() {
        assertNull(RecipeTransferPlan.assign(Collections.nCopies(10, List.of("iron")), Map.of("iron", 100L), 1, k -> 64));
        for(int amount : new int[]{0, -1, 65, Integer.MAX_VALUE}) assertNull(RecipeTransferPlan.assign(List.of(List.of("iron")), Map.of("iron", Long.MAX_VALUE), amount, k -> 64));
    }
}
