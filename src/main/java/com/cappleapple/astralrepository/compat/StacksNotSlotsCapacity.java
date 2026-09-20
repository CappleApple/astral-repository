package com.cappleapple.astralrepository.compat;

import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Soft bridge to the installed Stacks Not Slots 1.x public exact capacity semantics. */
public final class StacksNotSlotsCapacity {
    private static boolean failed;
    private StacksNotSlotsCapacity() { }
    public static Fraction unitCost(ItemStack stack) {
        if (stack.isEmpty()) return new Fraction(BigInteger.ZERO, BigInteger.ONE);
        if (!failed && CompatConfig.stacksNotSlots.get() && ModList.get().isLoaded("stacksnotslots")) {
            try {
                Object exact = OptionalApi.call(null, "com.cappleapple.stacksnotslots.api.StacksNotSlotsApi",
                        "exactCapacityCost", new Class<?>[]{ItemStack.class}, stack.copyWithCount(1));
                return new Fraction((BigInteger)OptionalApi.call(exact, "com.cappleapple.stacksnotslots.api.CapacityAmount", "numerator"),
                        (BigInteger)OptionalApi.call(exact, "com.cappleapple.stacksnotslots.api.CapacityAmount", "denominator"));
            } catch (RuntimeException | LinkageError failure) {
                failed = true;
                LogUtils.getLogger().error("Stacks Not Slots capacity API unavailable; using native stack-size accounting", failure);
            }
        }
        return new Fraction(BigInteger.valueOf(64), BigInteger.valueOf(Math.max(1, stack.getMaxStackSize())));
    }
    public record Fraction(BigInteger numerator, BigInteger denominator) {
        public Fraction {
            if (numerator.signum() < 0 || denominator.signum() <= 0) throw new IllegalArgumentException("Invalid capacity fraction");
            BigInteger divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor); denominator = denominator.divide(divisor);
        }
        public long ceil(long count) {
            if (count <= 0) return 0;
            BigInteger cost = numerator.multiply(BigInteger.valueOf(count)).add(denominator).subtract(BigInteger.ONE).divide(denominator);
            return cost.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }
    }
}