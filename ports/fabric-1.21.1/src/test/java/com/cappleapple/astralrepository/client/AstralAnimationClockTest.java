package com.cappleapple.astralrepository.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AstralAnimationClockTest {
    @Test void normalSpeedRetainsVanillaPhase() {
        assertEquals(.175F, AstralAnimationClock.phase(24000L * 12345, .175F, 1));
    }
    @Test void zeroFreezesEveryWorldAge() {
        assertEquals(0, AstralAnimationClock.phase(987654321, .987F, 0));
    }
    @Test void halfSpeedContinuesAcrossVanillaWrap() {
        float before = AstralAnimationClock.phase(23999, 23999F / 24000, .5);
        float after = AstralAnimationClock.phase(24000, 0, .5);
        assertEquals(.5F, after);
        assertEquals(.5 / 24000, after - before, .0000001);
    }
    @Test void arbitraryRateRetainsDayCarry() {
        assertEquals(.3F, AstralAnimationClock.phase(24000, 0, .3));
        assertEquals(.6F, AstralAnimationClock.phase(48000, 0, .3));
        assertEquals(.9F, AstralAnimationClock.phase(72000, 0, .3));
    }
    @Test void fasterRatesStayInOneCycle() {
        assertEquals(.5F, AstralAnimationClock.phase(0, .25F, 10));
        for (double speed : new double[] { .01, .3, .5, 1, 2.5, 10 }) {
            float phase = AstralAnimationClock.phase(1_000_000_000, .987F, speed);
            assertTrue(phase >= 0 && phase < 1);
        }
    }
}
