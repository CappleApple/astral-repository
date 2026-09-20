package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.menu.NexusViewRequests;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class NexusViewRequestsTest {
    @Test void delayedWindowCannotUndoRapidScrollingOrTheNextWheelStep() {
        var requests = new NexusViewRequests();
        int row = 0;
        for (int i = 1; i <= 3; i++) { row++; assertEquals(i, requests.next()); }
        row = requests.resolveRow(row, 1, 1, false);
        assertEquals(3, row);
        row++;
        assertEquals(4, requests.next());
        assertEquals(4, row);
        assertEquals(4, requests.resolveRow(row, 3, 3, false));
        assertEquals(4, requests.resolveRow(row, 4, 4, false));
    }
    @Test void latestAcknowledgementCanClampTheWindowWhileOldProgressCannot() {
        var requests = new NexusViewRequests();
        requests.next(); requests.next();
        assertEquals(50, requests.resolveRow(50, 0, 1, false));
        assertEquals(7, requests.resolveRow(50, 7, 2, false));
        assertEquals(4, requests.resolveRow(7, 4, 2, false));
    }
    @Test void searchDebounceKeepsItsResetRowBeforeANewRequestIsSent() {
        var requests = new NexusViewRequests();
        requests.next();
        assertEquals(0, requests.resolveRow(0, 9, 1, true));
        requests.next();
        assertEquals(0, requests.resolveRow(0, 9, 1, false));
        assertEquals(0, requests.resolveRow(0, 0, 2, false));
    }
}
