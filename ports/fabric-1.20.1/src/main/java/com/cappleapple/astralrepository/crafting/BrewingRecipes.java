package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;

/** Concrete, component-sensitive recipes resolved through the server's brewing rules. */
public final class BrewingRecipes {
    public record Entry(ResourceLocation id, ItemStack input, ItemStack reagent, ItemStack output) {}
    private record Catalogue(Map<ItemKey, ItemStack> inputs, Map<ItemKey, ItemStack> reagents, List<Entry> entries) {}
    private static final Map<Object, Catalogue> BASE = new WeakHashMap<>();

    public static List<Entry> entries(ServerLevel level, Collection<ItemStack> stock) {
        Object key = cacheKey(level);
        Catalogue base = BASE.computeIfAbsent(key, ignored -> build(level));
        Map<ItemKey, ItemStack> inputs = new LinkedHashMap<>(), reagents = new LinkedHashMap<>();
        for (var stack : stock) if (!stack.isEmpty()) {
            ItemKey identity = new ItemKey(stack);
            if (!base.inputs().containsKey(identity) && isInput(level, stack)) inputs.put(identity, stack.copyWithCount(1));
            if (!base.reagents().containsKey(identity) && isIngredient(level, stack)) reagents.put(identity, stack.copyWithCount(1));
        }
        if (inputs.isEmpty() && reagents.isEmpty()) return base.entries();
        List<Entry> result = new ArrayList<>(base.entries());
        for (var input : inputs.values()) {
            for (var reagent : base.reagents().values()) add(level, result, input, reagent);
            for (var reagent : reagents.values()) add(level, result, input, reagent);
        }
        for (var input : base.inputs().values()) for (var reagent : reagents.values()) add(level, result, input, reagent);
        return List.copyOf(result);
    }
    private static Catalogue build(ServerLevel level) {
        Map<ItemKey, ItemStack> inputs = new LinkedHashMap<>(), reagents = new LinkedHashMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (isIngredient(level, stack)) reagents.put(new ItemKey(stack), stack);
            if (isInput(level, stack)) inputs.put(new ItemKey(stack), stack);
            if (item instanceof PotionItem) {
                for (var potion : BuiltInRegistries.POTION.holders().toList()) {
                    ItemStack bottle = PotionUtils.setPotion(new ItemStack(item), potion.value());
                    inputs.put(new ItemKey(bottle), bottle);
                }
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (var input : inputs.values()) for (var reagent : reagents.values()) add(level, entries, input, reagent);
        return new Catalogue(Map.copyOf(inputs), Map.copyOf(reagents), List.copyOf(entries));
    }
    private static void add(ServerLevel level, List<Entry> entries, ItemStack input, ItemStack reagent) {
        if (!hasMix(level, input, reagent)) return;
        ItemStack output = mix(level, reagent, input.copy());
        if (output.isEmpty() || ItemStack.isSameItemSameTags(input, output)) return;
        String identity = input.save(new net.minecraft.nbt.CompoundTag()) + "\n" + reagent.save(new net.minecraft.nbt.CompoundTag());
        ResourceLocation id = new ResourceLocation("astral_repository", "brewing/"
                + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""));
        entries.add(new Entry(id, input.copy(), reagent.copy(), output.copy()));
    }
    private static Object cacheKey(ServerLevel level) { return level.getServer(); }
    static boolean isInput(ServerLevel level, ItemStack stack) { return stack.getItem() instanceof PotionItem; }
    static boolean isIngredient(ServerLevel level, ItemStack stack) { return net.minecraft.world.item.alchemy.PotionBrewing.isIngredient(stack); }
    static boolean hasMix(ServerLevel level, ItemStack input, ItemStack reagent) { return net.minecraft.world.item.alchemy.PotionBrewing.hasMix(input, reagent); }
    static ItemStack mix(ServerLevel level, ItemStack reagent, ItemStack input) { return net.minecraft.world.item.alchemy.PotionBrewing.mix(reagent, input); }
    private BrewingRecipes() {}
}
