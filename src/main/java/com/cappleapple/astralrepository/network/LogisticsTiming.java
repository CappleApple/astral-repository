package com.cappleapple.astralrepository.network;

import com.cappleapple.astralrepository.AstralConfig;
import java.util.function.Supplier;

/** Server transaction timing; scoped player effects never change automatic processing semantics. */
public final class LogisticsTiming {
    public static final int DELAY_TICKS = 20;
    private static final ThreadLocal<Boolean> PLAYER = ThreadLocal.withInitial(() -> false);

    public static int playerDelayTicks() { return AstralConfig.instantPlayerInteractions.get() ? 0 : DELAY_TICKS; }
    public static boolean automaticDue(long tick) { return AstralConfig.instantAutomaticLogistics.get() || tick % DELAY_TICKS == 0; }
    public static int visualTicks(int normalTicks) {
        if(PLAYER.get())return Math.max(20,normalTicks);
        boolean instant = AstralConfig.instantAutomaticLogistics.get();
        return instant ? 1 : normalTicks;
    }
    public static <T> T playerInteraction(Supplier<T> action) { return scoped(true, action); }
    public static void playerInteraction(Runnable action) { playerInteraction(() -> { action.run(); return null; }); }
    public static <T> T automaticOperation(Supplier<T> action) { return scoped(false, action); }
    private static <T> T scoped(boolean player, Supplier<T> action) {
        boolean previous = PLAYER.get();
        PLAYER.set(player);
        try { return action.get(); }
        finally { if (previous) PLAYER.set(true); else PLAYER.remove(); }
    }
    private LogisticsTiming() {}
}
