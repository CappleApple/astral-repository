package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.*;
import java.util.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** One server-thread catalog's immutable identities and reusable read-only ingredient prototypes. */
final class NativeStockSamples {
    private record Sample(ItemKey key, ItemStack prototype) {}
    private record FluidSample(FluidKey key, Object prototype) {}
    private final List<Sample> items;
    private final Map<Item, List<Sample>> byItem;
    private final Map<Ingredient, List<ItemStack>> representations = new IdentityHashMap<>();
    private final Map<Ingredient, List<ItemKey>> matching = new IdentityHashMap<>();
    private final List<FluidSample> fluids;
    private final Map<Object, List<FluidKey>> matchingFluids = new IdentityHashMap<>();
    NativeStockSamples(Map<ResourceKey, Long> snapshot) {
        List<Sample> all = new ArrayList<>(); Map<Item, List<Sample>> grouped = new IdentityHashMap<>();
        List<FluidSample> fluidSamples = new ArrayList<>();
        for (ResourceKey key : snapshot.keySet()) {
            if (key instanceof ItemKey item) {
                ItemStack sample = item.sample(); Sample entry = new Sample(item, sample); all.add(entry);
                grouped.computeIfAbsent(sample.getItem(), ignored -> new ArrayList<>()).add(entry);
            } else if (key instanceof FluidKey fluid) fluidSamples.add(new FluidSample(fluid, fluid.sample()));
        }
        fluids = List.copyOf(fluidSamples);
        items = List.copyOf(all); Map<Item, List<Sample>> immutable = new IdentityHashMap<>();
        grouped.forEach((item, entries) -> immutable.put(item, List.copyOf(entries))); byItem = Collections.unmodifiableMap(immutable);
    }
    List<ItemStack> representations(Ingredient ingredient) {
        return representations.computeIfAbsent(ingredient, value -> value.items().map(ItemStack::new).filter(stack -> !stack.isEmpty()).toList());
    }
    List<ItemKey> matching(Ingredient ingredient) {
        return matching.computeIfAbsent(ingredient, value -> {
            boolean simple = completeVanillaDomain(value);
            List<Sample> candidates = items;
            if (simple && !representations(value).isEmpty()) {
                Set<Item> types = Collections.newSetFromMap(new IdentityHashMap<>());
                representations(value).forEach(stack -> types.add(stack.getItem()));
                List<Sample> narrowed = new ArrayList<>(); types.forEach(item -> narrowed.addAll(byItem.getOrDefault(item, List.of()))); candidates = narrowed;
            }
            List<ItemKey> result = new ArrayList<>();
            for (Sample sample : candidates) if (Boolean.TRUE.equals(NativeBackendReflection.call(value, "test", sample.prototype()))) result.add(sample.key());
            return List.copyOf(result);
        });
    }
    List<FluidKey> matchingFluid(Object ingredient) {
        return matchingFluids.computeIfAbsent(ingredient, value -> {
            List<FluidKey> result = new ArrayList<>();
            for (var sample : fluids) if (Boolean.TRUE.equals(NativeBackendReflection.call(value, "test", sample.prototype()))) result.add(sample.key());
            return List.copyOf(result);
        });
    }
    private static boolean completeVanillaDomain(Ingredient ingredient) {
        if (ingredient.getClass() != Ingredient.class) return false;
        try {
            if (NativeBackendReflection.optional(ingredient, "getCustomIngredient") != null
                    || Boolean.TRUE.equals(NativeBackendReflection.optional(ingredient, "isCustom"))) return false;
            Object declared = NativeBackendReflection.optional(ingredient, "getValues");
            if (declared == null) return false;
            String item = "L" + ItemStack.class.getName().replace('.', '/') + ";";
            String tag = "L" + net.minecraft.tags.TagKey.class.getName().replace('.', '/') + ";";
            for (Object value : NativeBackendReflection.list(declared)) {
                if (value == null || value.getClass().getEnclosingClass() != Ingredient.class) return false;
                var fields = NativeBytecode.inspect(value.getClass()).fields().stream().filter(field -> (field.access() & 8) == 0).toList();
                if (fields.size() != 1 || !(fields.getFirst().descriptor().equals(item) || fields.getFirst().descriptor().equals(tag))) return false;
            }
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
}
