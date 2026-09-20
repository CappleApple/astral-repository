package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.content.RuneCadence;
import com.cappleapple.astralrepository.content.RuneCadence.Kind;
import com.cappleapple.astralrepository.content.RuneCadence.Rate;
import com.cappleapple.astralrepository.network.RuneTransferSchedule.Bounds;
import com.cappleapple.astralrepository.network.RuneTransferSchedule.Limits;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuneTransferScheduleTest {
    private static final int ALL = 15;
    private static Limits limits(int amount, int ticks) {
        var bound = new Bounds(amount, Integer.MAX_VALUE, 1);
        return new Limits(ticks, bound, bound, bound, bound);
    }
    private static RuneCadence onlyItems(int amount, int ticks) {
        var rates = new EnumMap<Kind, Rate>(Kind.class);
        for (var kind : Kind.values()) rates.put(kind, new Rate(0, 1));
        rates.put(Kind.ITEMS, new Rate(amount, ticks));
        return new RuneCadence(rates);
    }

    @Test void defaultsWaitForGlobalAlignmentAndEachAttemptStartsItsOwnCooldown() {
        var schedule = new RuneTransferSchedule(); var limits = limits(16, 5);
        assertEquals(0, schedule.due(2, RuneCadence.DEFAULT, limits));
        assertEquals(0, schedule.due(4, RuneCadence.DEFAULT, limits));
        assertEquals(ALL, schedule.due(5, RuneCadence.DEFAULT, limits));
        assertEquals(0, schedule.due(9, RuneCadence.DEFAULT, limits));
        assertEquals(ALL, schedule.due(12, RuneCadence.DEFAULT, limits), "A late tick attempts once without catching up missed transfers");
        assertEquals(0, schedule.due(15, RuneCadence.DEFAULT, limits));
        assertEquals(ALL, schedule.due(17, RuneCadence.DEFAULT, limits));
    }

    @Test void explicitRatesStartImmediatelyAndScheduleEachMediumIndependently() {
        var schedule = new RuneTransferSchedule(); var limits = limits(16, 20);
        var rates = new EnumMap<Kind, Rate>(Kind.class);
        for (var kind : Kind.values()) rates.put(kind, new Rate(kind.ordinal() + 1, kind.ordinal() + 2));
        var cadence = new RuneCadence(rates);
        assertEquals(ALL, schedule.due(7, cadence, limits));
        assertEquals(0, schedule.due(8, cadence, limits));
        assertEquals(Kind.ITEMS.bit(), schedule.due(9, cadence, limits));
        assertEquals(Kind.FLUID.bit(), schedule.due(10, cadence, limits));
        assertEquals(Kind.ITEMS.bit() | Kind.ENERGY.bit(), schedule.due(11, cadence, limits));
        assertEquals(Kind.SOURCE.bit(), schedule.due(12, cadence, limits));
        assertEquals(Kind.ITEMS.bit() | Kind.FLUID.bit(), schedule.due(13, cadence, limits));
        for (var kind : Kind.values()) assertEquals(kind.ordinal() + 1, schedule.amount(kind));
    }

    @Test void changingAmountOrIntervalUsesLastAttemptInsteadOfEditTime() {
        var schedule = new RuneTransferSchedule(); var limits = limits(16, 20);
        assertEquals(1, schedule.due(7, onlyItems(16, 10), limits));
        assertEquals(0, schedule.due(8, onlyItems(32, 10), limits));
        assertEquals(0, schedule.due(9, onlyItems(32, 5), limits));
        assertEquals(0, schedule.due(11, onlyItems(32, 5), limits));
        assertEquals(1, schedule.due(12, onlyItems(32, 5), limits));
        assertEquals(0, schedule.due(13, onlyItems(32, 10), limits));
        assertEquals(0, schedule.due(21, onlyItems(32, 10), limits));
        assertEquals(1, schedule.due(22, onlyItems(32, 10), limits));
    }

    @Test void equalEffectiveEditsPreserveTheOriginalDefaultDeadline() {
        var schedule = new RuneTransferSchedule(); var limits = limits(16, 10);
        assertEquals(0, schedule.due(3, RuneCadence.DEFAULT, limits));
        var explicit = RuneCadence.DEFAULT.with(Kind.ITEMS, new Rate(16, 10));
        assertEquals(0, schedule.due(4, explicit, limits), "Adding an equivalent override must not make its first transfer immediate");
        assertEquals(0, schedule.due(5, RuneCadence.DEFAULT, limits));
        assertEquals(0, schedule.due(9, explicit, limits));
        assertEquals(ALL, schedule.due(10, explicit, limits));
        var clonedLimits = limits(16, 10);
        assertNotSame(limits, clonedLimits);
        assertSame(limits, clonedLimits.reuse(limits), "Unchanged tick snapshots preserve identity for every rune");
        assertEquals(0, schedule.due(11, new RuneCadence(explicit.overrides()), clonedLimits));
        assertEquals(ALL, schedule.due(20, explicit, limits));
    }

    @Test void anEffectiveEditRebuildsDeadlinesForOtherNeverAttemptedMedia() {
        var schedule = new RuneTransferSchedule(); var limits = limits(16, 10);
        assertEquals(0, schedule.due(3, RuneCadence.DEFAULT, limits));
        var explicit = RuneCadence.DEFAULT.with(Kind.ITEMS, new Rate(16, 10));
        assertEquals(0, schedule.due(4, explicit, limits));
        var changed = explicit.with(Kind.FLUID, new Rate(17, 10));
        assertEquals(Kind.ITEMS.bit() | Kind.FLUID.bit(), schedule.due(5, changed, limits),
                "A changed resolved cadence rebuilds all never-attempted deadlines using current override presence");
        assertEquals(Kind.ENERGY.bit() | Kind.SOURCE.bit(), schedule.due(10, changed, limits));
    }

    @Test void longUptimeRetainsIntegerTickPrecisionAndFullSizeAmounts() {
        var schedule = new RuneTransferSchedule(); var limits = limits(Integer.MAX_VALUE, 3);
        long clock = (1L << 40) + 7, aligned = clock + Math.floorMod(-clock, 3);
        assertEquals(0, schedule.due(clock, RuneCadence.DEFAULT, limits));
        assertEquals(ALL, schedule.due(aligned, RuneCadence.DEFAULT, limits));
        assertEquals(Integer.MAX_VALUE, schedule.amount(Kind.ITEMS));
        assertEquals(0, schedule.due(aligned + 2, RuneCadence.DEFAULT, limits));
        assertEquals(ALL, schedule.due(aligned + 3, RuneCadence.DEFAULT, limits));
    }

    @Test void changedDefaultsAndInstantTimingApplyWithoutResettingPastAttempts() {
        var schedule = new RuneTransferSchedule(); var original = limits(16, 10);
        assertEquals(ALL, schedule.due(10, RuneCadence.DEFAULT, original));
        var slower = limits(32, 20);
        assertSame(slower, slower.reuse(original));
        assertEquals(0, schedule.due(11, RuneCadence.DEFAULT, slower));
        assertEquals(32, schedule.amount(Kind.ITEMS));
        assertEquals(0, schedule.due(29, RuneCadence.DEFAULT, slower));
        assertEquals(ALL, schedule.due(30, RuneCadence.DEFAULT, slower));
        assertEquals(ALL, schedule.due(31, RuneCadence.DEFAULT, limits(32, 1)));
    }

    @Test void allServerMaximumsAndMinimumIntervalsConstrainOverridesAndDefaults() {
        var limits = new Limits(1, new Bounds(200, 64, 3), new Bounds(4000, 1000, 4),
                new Bounds(50000, 10000, 5), new Bounds(5000, 1000, 6));
        var defaults = new RuneTransferSchedule();
        assertEquals(0, defaults.due(1, RuneCadence.DEFAULT, limits));
        assertEquals(Kind.ITEMS.bit(), defaults.due(3, RuneCadence.DEFAULT, limits));
        var requested = new EnumMap<Kind, Rate>(Kind.class);
        for (var kind : Kind.values()) requested.put(kind, new Rate(Integer.MAX_VALUE, 1));
        var override = new RuneCadence(requested); var explicit = new RuneTransferSchedule();
        assertEquals(ALL, explicit.due(1, override, limits));
        assertEquals(0, explicit.due(2, override, limits));
        assertEquals(Kind.ITEMS.bit(), explicit.due(4, override, limits));
        for (var kind : Kind.values()) {
            assertEquals(limits.bounds(kind).maximum(), defaults.amount(kind));
            assertEquals(limits.bounds(kind).maximum(), explicit.amount(kind));
        }
        var equivalent = override.with(Kind.ITEMS, new Rate(999, 2));
        assertEquals(Kind.FLUID.bit(), explicit.due(5, equivalent, limits));
        assertEquals(Kind.ENERGY.bit(), explicit.due(6, equivalent, limits));
        assertEquals(Kind.ITEMS.bit() | Kind.SOURCE.bit(), explicit.due(7, equivalent, limits));
    }

    @Test void zeroAmountsNeverAdvanceAttemptsBeforeOrAfterAnActiveCooldown() {
        var schedule = new RuneTransferSchedule(); var limits = limits(16, 10);
        assertEquals(0, schedule.due(3, onlyItems(0, 10), limits));
        assertEquals(0, schedule.due(50, onlyItems(0, 10), limits));
        assertEquals(1, schedule.due(51, onlyItems(1, 10), limits), "A never-attempted explicit rate starts immediately when enabled");
        assertEquals(0, schedule.due(52, onlyItems(0, 10), limits));
        assertEquals(0, schedule.due(70, onlyItems(0, 10), limits));
        assertEquals(0, schedule.due(71, onlyItems(1, 30), limits), "Disabled ticks must not replace the last active attempt at 51");
        assertEquals(0, schedule.due(80, onlyItems(1, 30), limits));
        assertEquals(1, schedule.due(81, onlyItems(1, 30), limits));
    }

    @Test void changingLimitsCannotTurnEquivalentConstrainedRatesIntoAnEarlyTransfer() {
        var bound = new Bounds(16, 64, 10); var limits = new Limits(10, bound, bound, bound, bound);
        var schedule = new RuneTransferSchedule(); var cadence = onlyItems(200, 2);
        assertEquals(1, schedule.due(7, cadence, limits));
        var irrelevantChange = new Limits(25, new Bounds(48, 64, 10), bound, bound, bound);
        assertEquals(0, schedule.due(8, cadence, irrelevantChange));
        assertEquals(0, schedule.due(16, cadence, limits));
        assertEquals(1, schedule.due(17, cadence, limits));
    }
}
