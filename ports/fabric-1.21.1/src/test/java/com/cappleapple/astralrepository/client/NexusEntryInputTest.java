package com.cappleapple.astralrepository.client;

import org.junit.jupiter.api.Test;
import static com.cappleapple.astralrepository.client.NexusEntryInput.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusEntryInputTest {
    @Test void clickingAnUnstockedStarChoosesQuantityWithoutSubmittingACraft() {
        assertEquals(SELECT_CRAFT, NexusEntryInput.action(0, false, false, true, 0));
        assertEquals(SELECT_CRAFT, NexusEntryInput.action(1, false, false, true, 0));
        assertEquals(SELECT_CRAFT, NexusEntryInput.action(0, true, false, true, 0));
    }

    @Test void storedCraftableItemsStillWithdrawAndMiddleClickChoosesQuantity() {
        assertEquals(PICKUP, NexusEntryInput.action(0, false, false, true, 64));
        assertEquals(PICKUP, NexusEntryInput.action(1, false, false, true, 64));
        assertEquals(QUICK_WITHDRAW, NexusEntryInput.action(0, true, false, true, 64));
        assertEquals(SELECT_CRAFT, NexusEntryInput.action(2, false, false, true, 64));
    }

    @Test void stackCraftingRemainsAnExplicitShortcut() {
        assertEquals(CRAFT_STACK, NexusEntryInput.action(1, true, false, true, 0));
        assertEquals(CRAFT_STACK, NexusEntryInput.action(1, true, false, true, 64));
        assertEquals(QUICK_WITHDRAW, NexusEntryInput.action(1, true, false, false, 64));
    }

    @Test void carriedItemsDepositAndEmptyCellsDoNothing() {
        assertEquals(DEPOSIT, NexusEntryInput.action(0, false, true, true, 0));
        assertEquals(DEPOSIT, NexusEntryInput.action(1, true, true, true, 64));
        assertEquals(NONE, NexusEntryInput.action(2, false, true, false, 0));
        assertEquals(NONE, NexusEntryInput.action(0, false, false, false, 0));
    }
}
