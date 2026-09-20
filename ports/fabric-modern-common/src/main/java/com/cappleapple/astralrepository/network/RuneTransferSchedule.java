package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.content.RuneCadence;
import com.cappleapple.astralrepository.content.RuneCadence.Kind;
import com.cappleapple.astralrepository.content.RuneCadence.Rate;
import java.util.Arrays;
import java.util.Objects;

/** Per-rune cooldowns. Owned by the server thread; scheduling never queries a provider or world. */
public final class RuneTransferSchedule {
    private static final Kind[] KINDS = Kind.values();

    public record Bounds(int defaultAmount, int maximum, int minimumTicks) {
        public Bounds {
            if (defaultAmount < 0 || maximum < 1 || minimumTicks < 1) throw new IllegalArgumentException("Invalid cadence bounds");
        }
        private static Bounds capture(Kind kind) { return new Bounds(kind.defaultAmount(), kind.maximum(), kind.minimumTicks()); }
    }

    /** One immutable config snapshot shared by all runes in a server tick. */
    public record Limits(int defaultTicks, Bounds items, Bounds fluid, Bounds energy, Bounds source) {
        public Limits {
            if (defaultTicks < 1) throw new IllegalArgumentException("Invalid default cadence");
            Objects.requireNonNull(items); Objects.requireNonNull(fluid); Objects.requireNonNull(energy); Objects.requireNonNull(source);
        }
        public Bounds bounds(Kind kind) {
            return switch (kind) { case ITEMS -> items; case FLUID -> fluid; case ENERGY -> energy; case SOURCE -> source; };
        }
        /** Capture once at the start of a server tick, retaining identity while effective config is unchanged. */
        public static Limits capture(Limits previous) {
            return new Limits(AstralConfig.instantAutomaticLogistics.get() ? 1 : AstralConfig.runeTransferInterval.get(),
                    Bounds.capture(Kind.ITEMS), Bounds.capture(Kind.FLUID), Bounds.capture(Kind.ENERGY), Bounds.capture(Kind.SOURCE)).reuse(previous);
        }
        public Limits reuse(Limits previous) { return equals(previous) ? previous : this; }
    }

    private RuneCadence raw;
    private Limits limits;
    private final Rate[] rates = new Rate[KINDS.length];
    private final long[] next = new long[KINDS.length], attempted = new long[KINDS.length];

    public RuneTransferSchedule() { Arrays.fill(attempted, Long.MIN_VALUE); }

    /** Marks due media as attempted, including attempts which later find no route, resource, or capacity. */
    public int due(long clock, RuneCadence cadence, Limits currentLimits) {
        Objects.requireNonNull(cadence); Objects.requireNonNull(currentLimits);
        if (raw != cadence || limits != currentLimits) resolve(clock, cadence, currentLimits);
        int due = 0;
        for (int i = 0; i < rates.length; i++) {
            Rate rate = rates[i];
            if (clock >= next[i] && rate.amount() > 0) {
                due |= 1 << i; attempted[i] = clock; next[i] = clock + rate.ticks();
            }
        }
        return due;
    }

    public int amount(Kind kind) {
        Rate rate = rates[kind.ordinal()];
        if (rate == null) throw new IllegalStateException("Schedule has not been resolved");
        return rate.amount();
    }

    private void resolve(long clock, RuneCadence cadence, Limits currentLimits) {
        boolean changed = raw == null;
        for (Kind kind : KINDS) {
            Bounds bounds = currentLimits.bounds(kind); Rate override = cadence.overrides().get(kind);
            Rate rate = new Rate(Math.min(override == null ? bounds.defaultAmount() : override.amount(), bounds.maximum()),
                    Math.max(override == null ? currentLimits.defaultTicks() : override.ticks(), bounds.minimumTicks()));
            int i = kind.ordinal();
            if (!rate.equals(rates[i])) { rates[i] = rate; changed = true; }
        }
        raw = cadence; limits = currentLimits;
        if (!changed) return;
        // Any effective rate change previously rebuilt all deadlines. Preserve that behavior,
        // including default alignment for media which have never made an attempt.
        for (Kind kind : KINDS) {
            int i = kind.ordinal(), interval = rates[i].ticks();
            next[i] = attempted[i] != Long.MIN_VALUE ? attempted[i] + interval
                    : cadence.overrides().containsKey(kind) ? clock : clock + Math.floorMod(-clock, interval);
        }
    }
}
