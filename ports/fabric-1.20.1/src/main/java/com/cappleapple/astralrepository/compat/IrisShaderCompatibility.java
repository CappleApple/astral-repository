package com.cappleapple.astralrepository.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.fabricmc.loader.api.FabricLoader;

/** Optional public Iris API; keep the active pack query live across shader toggles. */
public final class IrisShaderCompatibility {
    private static final BooleanSupplier PACK_IN_USE = findPackQuery();

    public static boolean isShaderPackInUse() {
        return PACK_IN_USE.getAsBoolean();
    }

    private static BooleanSupplier findPackQuery() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return () -> false;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method query = api.getMethod("isShaderPackInUse");
            return new BooleanSupplier() {
                private boolean failed;

                @Override public boolean getAsBoolean() {
                    if (failed) return true;
                    try {
                        return (boolean) query.invoke(instance);
                    } catch (ReflectiveOperationException | LinkageError failure) {
                        failed = true;
                        logFallback(failure);
                        return true;
                    }
                }
            };
        } catch (ReflectiveOperationException | LinkageError failure) {
            logFallback(failure);
            return () -> true;
        }
    }

    private static void logFallback(Throwable failure) {
        LogUtils.getLogger().warn("Iris shader state unavailable; using ordinary Astral textures", failure);
    }

    private IrisShaderCompatibility() {}
}
