package com.cappleapple.astralrepository.compat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProviderGuardTest {
    @Test void readFailureIsQuarantinedWithoutAffectingAnotherProvider() {
        ProviderGuard broken=new ProviderGuard("broken");
        ProviderGuard healthy=new ProviderGuard("healthy");
        AtomicInteger attempts=new AtomicInteger();
        assertEquals(0,broken.read(() -> { attempts.incrementAndGet(); throw new IllegalStateException("broken capability"); },0));
        assertEquals(0,broken.read(() -> { attempts.incrementAndGet(); return 9; },0));
        assertEquals(1,attempts.get());
        assertEquals(12,healthy.read(() -> 12,0));
        assertTrue(broken.failed());
    }
    @Test void uncertainCommitIsNotReportedAsASafeRemainder() {
        ProviderGuard guard=new ProviderGuard("mutated_then_failed");
        AtomicInteger backend=new AtomicInteger();
        assertThrows(ProviderFailure.class,() -> guard.transfer(() -> {
            backend.addAndGet(8);
            throw new IllegalStateException("backend failed after commit");
        },0,false));
        assertEquals(8,backend.get());
        assertEquals(0,guard.transfer(() -> { backend.addAndGet(8); return 8; },0,false));
        assertEquals(8,backend.get());
    }
    @Test void failedSimulationCannotCommitAnything() {
        ProviderGuard guard=new ProviderGuard("simulation");
        assertEquals(0,guard.transfer(() -> { throw new IllegalArgumentException("invalid simulated resource"); },0,true));
        assertTrue(guard.failed());
    }
}