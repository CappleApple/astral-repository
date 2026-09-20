package com.cappleapple.astralrepository.api;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingContextTest {
    @Test void bridgeTokensRejectReturnToAnyVisitedProvider() {
        var root=CraftingContext.root();
        var ae=root.enter("ae2:network-a");
        var rs=ae.enter("rs:network-b");
        assertThrows(IllegalStateException.class,() -> rs.enter("ae2:network-a"));
        assertTrue(root.visitedProviders().isEmpty());
        assertEquals(root.requestId(),rs.requestId());
    }
    @Test void depthBoundPreventsUnboundedChainsOfDifferentNetworks() {
        var root=new CraftingContext(UUID.randomUUID(),Set.of(),2);
        var two=root.enter("first").enter("second");
        assertThrows(IllegalStateException.class,() -> two.enter("third"));
        assertThrows(UnsupportedOperationException.class,() -> two.visitedProviders().add("fourth"));
    }
}