package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.cappleapple.astralrepository.crafting.MekanismRecipeDiscovery.*;

/** Converts native recipe/resource descriptions; never creates a physical machine result. */
public final class MekanismResourceSupport {
    private MekanismResourceSupport() {}
    public static long amount(Object stack) {
        if (stack == null) return 0;
        if (stack instanceof ItemStack item) return item.getCount();
        if (stack instanceof Number scalar) return scalar.longValue();
        if (hasMethod(stack, "isEmpty") && Boolean.TRUE.equals(invoke(stack, "isEmpty"))) return 0;
        return ((Number) invoke(stack, "getAmount")).longValue();
    }
    public static ResourceKey key(Object stack) {
        if (stack instanceof ItemStack item) return new ItemKey(item);
        if (stack instanceof Number) return ResourceKinds.FE;
        if (resource(stack).equals("chemical")) return ChemicalKey.fromNative(stack);
        if (resource(stack).equals("fluid")) {
            for (Constructor<?> constructor : FluidKey.class.getConstructors()) if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].isInstance(stack)) {
                try { return (ResourceKey) constructor.newInstance(stack); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native fluid identity could not be read", failure); }
            }
        }
        throw new IllegalArgumentException("Unsupported native resource " + stack.getClass().getName());
    }
    public static long energyToNetwork(long nativeAmount) { return energyConvert(nativeAmount, "convertToAsLong"); }
    public static long energyFromNetwork(long networkAmount) { return energyConvert(networkAmount, "convertFrom"); }
    private static long energyConvert(long amount, String method) {
        try {
            Object unit = Class.forName("mekanism.common.util.UnitDisplayUtils$EnergyUnit").getField("FORGE_ENERGY").get(null);
            return ((Number) invoke(unit, method, new Class<?>[]{long.class}, amount)).longValue();
        } catch (ReflectiveOperationException unsupported) { throw new IllegalStateException("Native energy conversion API is unavailable", unsupported); }
    }
    public static String kind(ResourceKey key) { return key instanceof ItemKey ? "item" : key instanceof FluidKey ? "fluid" : key instanceof ChemicalKey ? "chemical" : "energy"; }
    public static Object sample(ResourceKey key, long amount) {
        if (key instanceof ItemKey item) return item.sample().copyWithCount(Math.toIntExact(amount));
        if (key instanceof ChemicalKey chemical) return chemical.sampleWithAmount(amount);
        if (key instanceof FluidKey fluid) {
            Object sample = fluid.sample();
            if (hasMethod(sample, "copy")) sample = invoke(sample, "copy");
            return invoke(sample, "copyWithAmount", new Class<?>[]{int.class}, Math.toIntExact(amount));
        }
        return amount;
    }
    public static Map<ResourceKey, Long> flatten(Object output) {
        Map<ResourceKey, Long> result = new LinkedHashMap<>(); flatten(output, result, 0); return Map.copyOf(result);
    }
    private static void flatten(Object value, Map<ResourceKey, Long> result, int depth) {
        if (value == null || depth > 4) return;
        if (value instanceof Optional<?> optional) { optional.ifPresent(part -> flatten(part, result, depth + 1)); return; }
        if (value instanceof Collection<?> values) { values.forEach(part -> flatten(part, result, depth + 1)); return; }
        if (value instanceof ItemStack || value instanceof Number || !resource(value).equals("unknown")) {
            long amount = amount(value); if (value instanceof Number) amount = energyToNetwork(amount);
            if (amount > 0) result.merge(key(value), amount, Math::addExact); return;
        }
        if (value.getClass().isRecord()) {
            for (RecordComponent component : value.getClass().getRecordComponents()) flatten(invoke(value, component.getName()), result, depth + 1);
            return;
        }
        for (String getter : List.of("getLeftOutput", "getRightOutput", "getItemOutput", "getGasOutput", "getFluidOutput", "left", "right", "item", "chemical", "fluid", "optionalItem"))
            if (hasMethod(value, getter)) flatten(invoke(value, getter), result, depth + 1);
    }
}
