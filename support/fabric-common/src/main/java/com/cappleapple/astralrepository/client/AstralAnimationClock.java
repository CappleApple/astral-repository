package com.cappleapple.astralrepository.client;

/** Keeps fractional animation rates continuous across the vanilla shader clock's daily wrap. */
public final class AstralAnimationClock {
    public static float phase(long worldTicks, float shaderDayFraction, double speed) {
        double cycles = (Math.floorDiv(worldTicks, 24000L) + (double)shaderDayFraction) * speed;
        return (float)(cycles - Math.floor(cycles));
    }
    private AstralAnimationClock() {}
}
