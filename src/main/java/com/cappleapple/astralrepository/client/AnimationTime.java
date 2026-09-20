package com.cappleapple.astralrepository.client;

/** Reduce whole ticks before introducing fractional frames or float render transforms. */
public final class AnimationTime {
    public static double elapsed(long now, long started, float partialTick) {
        return (now - started) + (double)partialTick;
    }

    public static float rotation(long ticks, float partialTick, long periodTicks) {
        if (periodTicks <= 0) throw new IllegalArgumentException("Animation period must be positive");
        double phase = (Math.floorMod(ticks, periodTicks) + (double)partialTick) / periodTicks;
        return (float)((phase - Math.floor(phase)) * 360.0);
    }

    public static double radians(long ticks, float partialTick, double radiansPerTick) {
        // Keep partial frames out of the large product and bound the value before
        // trig functions or float transforms receive it.
        double whole = Math.IEEEremainder(ticks * radiansPerTick, (Math.PI*2));
        return Math.IEEEremainder(whole + partialTick * radiansPerTick, (Math.PI*2));
    }

    private AnimationTime() {}
}
