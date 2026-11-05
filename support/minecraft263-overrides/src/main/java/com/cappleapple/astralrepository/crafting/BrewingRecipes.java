package com.cappleapple.astralrepository.crafting;

import com.cappleapple.astralrepository.api.ItemKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;

/** Concrete variants resolved through Minecraft 26.3's native datapack brewing recipes. */
public final class BrewingRecipes {
    public record Entry(Identifier id, ItemStack input, ItemStack reagent, ItemStack output) {}
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
                for (var potion : BuiltInRegistries.POTION.listElements().toList()) {
                    ItemStack bottle = PotionContents.createItemStack(item, potion);
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
        if (output.isEmpty() || ItemStack.isSameItemSameComponents(input, output)) return;
        String identity = com.cappleapple.astralrepository.port.NbtCodecs.save(input, level.registryAccess()) + "\n" + com.cappleapple.astralrepository.port.NbtCodecs.save(reagent, level.registryAccess());
        Identifier id = Identifier.fromNamespaceAndPath("astral_repository", "brewing/"
                + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""));
        entries.add(new Entry(id, input.copy(), reagent.copy(), output.copy()));
    }
    private static Object cacheKey(ServerLevel level) { return level.recipeAccess().propertySet(RecipePropertySet.BREWING_INPUTS); }
    static boolean isInput(ServerLevel level, ItemStack stack) { return PotionIngredient.isPotionInput(stack, level.recipeAccess()); }
    static boolean isIngredient(ServerLevel level, ItemStack stack) { return level.recipeAccess().propertySet(RecipePropertySet.BREWING_REAGENTS).test(stack); }
    static boolean hasMix(ServerLevel level, ItemStack input, ItemStack reagent) { return level.recipeAccess().getRecipeFor(RecipeType.BREWING, new BrewingInput(input, reagent), level).isPresent(); }
    static ItemStack mix(ServerLevel level, ItemStack reagent, ItemStack input) { return level.recipeAccess().getRecipeFor(RecipeType.BREWING, new BrewingInput(input, reagent), level)
            .map(holder -> holder.value().assemble(new BrewingInput(input, reagent))).orElseGet(input::copy); }
    private BrewingRecipes() {}
}
