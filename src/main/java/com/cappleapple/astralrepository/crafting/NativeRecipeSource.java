package com.cappleapple.astralrepository.crafting;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Typed Minecraft calls remain correct when the loader remaps production method names. */
public final class NativeRecipeSource {
    private NativeRecipeSource() {}
    public record Entry(Identifier id, Recipe<?> value) {
        public RecipeType<?> type() { return value.getType(); }
    }
    public static List<Entry> entries(ServerLevel level) {
        return level.getServer().getRecipeManager().getRecipes().stream().map(holder -> new Entry(holder.id().identifier(), holder.value())).toList();
    }
    public static Entry entry(Object value) {
        if (value instanceof Entry entry) return entry;
        if (value instanceof RecipeHolder<?> holder) return new Entry(holder.id().identifier(), holder.value());
        return null;
    }
    public static Object nativeHolder(Entry entry) { return new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, entry.id()), entry.value()); }
    public static Object singleInput(ItemStack stack) { return new SingleRecipeInput(stack.copyWithCount(1)); }
    public static Ingredient nativeIngredient(Object value) { return value instanceof Ingredient ingredient ? ingredient : null; }
    public static RecipeType<?> type(Object recipe) { return ((Recipe<?>)recipe).getType(); }
    public static Identifier typeKey(Object recipe) { return BuiltInRegistries.RECIPE_TYPE.getKey(type(recipe)); }
    public static List<Ingredient> ingredients(Object recipe) { return com.cappleapple.astralrepository.port.RecipeCompat.ingredients((Recipe<?>)recipe).stream().flatMap(java.util.Optional::stream).toList(); }
    public static int[] shape(Object recipe) { return recipe instanceof ShapedRecipe shaped ? new int[]{shaped.getWidth(), shaped.getHeight()} : new int[]{0, 0}; }
    public static ItemStack output(Object recipe, RegistryAccess registries) { return com.cappleapple.astralrepository.port.RecipeCompat.output((Recipe<?>)recipe, registries); }
    /** Canonicalize native display samples only; never rewrite a physical storage identity. */
    public static ItemStack normalizeDisplay(ItemStack source, RegistryAccess registries) {
        ItemStack copy = source.copy();
        var contents = copy.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents != null && contents.potion().isPresent() && contents.potion().get().unwrapKey().isEmpty()) {
            var potion = contents.potion().get().value();
            var registered = registries.lookupOrThrow(net.minecraft.core.registries.Registries.POTION).listElements()
                    .filter(holder -> holder.value() == potion).findFirst();
            if (registered.isPresent()) contents = contents.withPotion(registered.get());
            else {
                var effects = new java.util.ArrayList<net.minecraft.world.effect.MobEffectInstance>();
                contents.getAllEffects().forEach(effect -> effects.add(new net.minecraft.world.effect.MobEffectInstance(effect)));
                contents = new net.minecraft.world.item.alchemy.PotionContents(java.util.Optional.empty(), contents.customColor(), List.copyOf(effects), contents.customName());
            }
            copy.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, contents);
        }
        for (var component : List.of(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) {
            var enchantments = copy.get(component); if (enchantments == null) continue;
            var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(enchantments);
            for (var entry : enchantments.entrySet()) if (entry.getKey().unwrapKey().isEmpty()) {
                var direct = entry.getKey();
                var registered = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).listElements()
                        .filter(holder -> holder.value() == direct.value()).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Native display enchantment is not registered"));
                mutable.removeIf(holder -> holder == direct); mutable.upgrade(registered, entry.getIntValue());
            }
            copy.set(component, mutable.toImmutable());
        }
        return copy;
    }
    public static int cookingTime(Object recipe) { return recipe instanceof AbstractCookingRecipe cooking ? cooking.cookingTime() : 1; }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean matches(Object recipe, Object input, Level level) { return ((Recipe)recipe).matches((RecipeInput)input, level); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ItemStack assemble(Object recipe, Object input, RegistryAccess registries) { return ((Recipe)recipe).assemble((RecipeInput)input); }
    public static Object craftingInput(List<ItemStack> items) { return CraftingInput.of(3, 3, items.stream().map(ItemStack::copy).toList()); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<ItemStack> remainingItems(Object recipe, Object input) { return com.cappleapple.astralrepository.port.RecipeCompat.remainders((Recipe<?>)recipe, (RecipeInput)input); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Entry> allFor(ServerLevel level, Object type) { return entries(level).stream().filter(entry -> entry.type() == type).toList(); }
}
