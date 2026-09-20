package com.cappleapple.astralrepository.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTimeTest {
    @Test void flightFramesRetainTheSamePrecisionAtEveryWorldAge() {
        for (long start : new long[] {0, 1L << 20, 1L << 24, 1L << 31, 1L << 40, Long.MAX_VALUE - 200}) {
            for (int tick = 0; tick < 100; tick++) {
                for (int frame = 0; frame < 64; frame++) {
                    float partial = frame / 64F;
                    assertEquals(tick + frame / 64.0, AnimationTime.elapsed(start + tick, start, partial), 0);
                }
            }
        }
    }

    @Test void itemSpinRetainsSubTickStepsAcrossLongUptimesAndPeriodBoundaries() {
        for (long tick : new long[] {0, 119, 120, 1L << 24, 1L << 31, 1L << 40, Long.MAX_VALUE - 1}) {
            float previous = AnimationTime.rotation(tick, 0, 120);
            for (int frame = 1; frame <= 64; frame++) {
                float current = frame == 64 ? AnimationTime.rotation(tick + 1, 0, 120)
                        : AnimationTime.rotation(tick, frame / 64F, 120);
                assertTrue(current >= 0 && current < 360);
                assertEquals(3 / 64.0, (current - previous + 360) % 360, 0.00004);
                previous = current;
            }
        }
    }

    @Test void crystalOrbitsKeepFractionalMovementAndContinuousTickBoundaries() {
        for (double rate : new double[] {.025, .06}) {
            for (long tick : new long[] {0, 1L << 24, 1L << 31, 1L << 40}) {
                double previous = AnimationTime.radians(tick, 0, rate);
                for (int frame = 1; frame <= 64; frame++) {
                    double current = frame == 64 ? AnimationTime.radians(tick + 1, 0, rate)
                            : AnimationTime.radians(tick, frame / 64F, rate);
                    assertTrue(Math.abs(current) <= Math.PI);
                    double advance = Math.IEEEremainder(current - previous, (Math.PI*2));
                    assertEquals(rate / 64, advance, frame == 64 ? 0.00001 : 0.000000001);
                    previous = current;
                }
            }
        }
    }
}
