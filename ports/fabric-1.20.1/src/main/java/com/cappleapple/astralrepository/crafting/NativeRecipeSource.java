package com.cappleapple.astralrepository.crafting;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

/** Typed Minecraft calls remain correct when the loader remaps production method names. */
public final class NativeRecipeSource {
    private NativeRecipeSource() {}
    public record Entry(ResourceLocation id, Recipe<?> value) {
        public RecipeType<?> type() { return value.getType(); }
    }
    public static List<Entry> entries(ServerLevel level) {
        return level.getRecipeManager().getRecipes().stream().map(holder -> new Entry(holder.getId(), holder)).toList();
    }
    public static Entry entry(Object value) {
        if (value instanceof Entry entry) return entry;
        if (value instanceof Recipe<?> holder) return new Entry(holder.getId(), holder);
        return null;
    }
    public static Object nativeHolder(Entry entry) { return entry.value(); }
    public static Object singleInput(ItemStack stack) { return new net.minecraft.world.SimpleContainer(stack.copyWithCount(1)); }
    public static Ingredient nativeIngredient(Object value) {
        if (value instanceof Ingredient ingredient) return ingredient;
        return value instanceof Ingredient.Value entry ? Ingredient.fromValues(java.util.stream.Stream.of(entry)) : null;
    }
    public static RecipeType<?> type(Object recipe) { return ((Recipe<?>)recipe).getType(); }
    public static ResourceLocation typeKey(Object recipe) { return BuiltInRegistries.RECIPE_TYPE.getKey(type(recipe)); }
    public static List<Ingredient> ingredients(Object recipe) { return List.copyOf(((Recipe<?>)recipe).getIngredients()); }
    public static int[] shape(Object recipe) { return recipe instanceof ShapedRecipe shaped ? new int[]{shaped.getWidth(), shaped.getHeight()} : new int[]{0, 0}; }
    public static ItemStack output(Object recipe, RegistryAccess registries) { return ((Recipe<?>)recipe).getResultItem(registries).copy(); }
    /** Canonicalize native display samples only; never rewrite a physical storage identity. */
    public static ItemStack normalizeDisplay(ItemStack source, RegistryAccess registries) { return source.copy(); }
    public static int cookingTime(Object recipe) { return recipe instanceof AbstractCookingRecipe cooking ? cooking.getCookingTime() : 1; }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean matches(Object recipe, Object input, Level level) { return ((Recipe)recipe).matches((Container)input, level); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ItemStack assemble(Object recipe, Object input, RegistryAccess registries) { return ((Recipe)recipe).assemble((Container)input, registries); }
    public static Object craftingInput(List<ItemStack> items) { return com.cappleapple.astralrepository.platform.Backport.craftingGrid(items); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<ItemStack> remainingItems(Object recipe, Object input) { return List.copyOf(((Recipe)recipe).getRemainingItems((Container)input)); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Entry> allFor(ServerLevel level, Object type) {
        List<Recipe<?>> holders = (List)level.getRecipeManager().getAllRecipesFor((RecipeType)type);
        return holders.stream().map(holder -> new Entry(holder.getId(), holder)).toList();
    }
}
