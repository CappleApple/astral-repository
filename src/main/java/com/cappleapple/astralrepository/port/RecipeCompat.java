package com.cappleapple.astralrepository.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.util.context.ContextMap;

/** Reads recipe placement and displays without changing the authoritative assembly path. */
public final class RecipeCompat {
    public static List<Optional<Ingredient>> ingredients(Recipe<?> recipe) {
        var placement = recipe.placementInfo();
        var result = new ArrayList<Optional<Ingredient>>();
        for (int index : placement.slotsToIngredientIndex()) result.add(index < 0 ? Optional.empty() : Optional.of(placement.ingredients().get(index)));
        return result;
    }
    public static ItemStack output(Recipe<?> recipe, net.minecraft.core.RegistryAccess registries) {
        var context = new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, registries).create(SlotDisplayContext.CONTEXT);
        return recipe.display().stream().map(display -> display.result().resolveForFirstStack(context)).filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
    }
    public static ItemStack[] samples(Optional<Ingredient> ingredient, net.minecraft.core.RegistryAccess registries) {
        return ingredient.map(value -> samples(value, registries)).orElseGet(() -> new ItemStack[0]);
    }
    public static ItemStack[] samples(Ingredient ingredient, net.minecraft.core.RegistryAccess registries) {
        var context = new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, registries).create(SlotDisplayContext.CONTEXT);
        return ingredient.display().resolveForStacks(context).toArray(ItemStack[]::new);
    }
    public static List<ItemStack> remainders(Recipe<?> recipe, RecipeInput input) {
        if (recipe instanceof CraftingRecipe crafting && input instanceof CraftingInput grid) return crafting.getRemainingItems(grid);
        var result = new ArrayList<ItemStack>(input.size());
        for (int i=0;i<input.size();i++) result.add(remainder(input.getItem(i)));
        return result;
    }
    public static ItemStack remainder(ItemStack stack) {
        var remainder = stack.getCraftingRemainder();
        return remainder == null ? ItemStack.EMPTY : remainder.create();
    }
    private RecipeCompat() {}
}

