package com.cappleapple.astralrepository.crafting;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import java.util.*;

/** Read-only bridges for machine recipes whose vanilla display methods omit their data. */
public final class MachineRecipeSupport {
    public record Input(Ingredient ingredient, int count) {}
    private MachineRecipeSupport() {}

    public static ItemStack output(Recipe<?> recipe, RegistryAccess registries) {
        if (MekanismRecipeDiscovery.implementsContract(recipe.getClass(), "mekanism.api.recipes.MekanismRecipe")) {
            String method = MekanismRecipeDiscovery.hasMethod(recipe, "getMainOutputDefinition") ? "getMainOutputDefinition" : "getOutputDefinition";
            if (MekanismRecipeDiscovery.hasMethod(recipe, method)) {
                Object definitions = MekanismRecipeDiscovery.invoke(recipe, method);
                if (definitions instanceof Collection<?> alternatives) for (Object alternative : alternatives)
                    for (var output : MekanismResourceSupport.flatten(alternative).entrySet()) if (output.getKey() instanceof com.cappleapple.astralrepository.api.ItemKey item)
                        return item.sample().copyWithCount(Math.toIntExact(output.getValue()));
            }
            return ItemStack.EMPTY;
        }
        if (recipe.getClass().getName().equals("rearth.oritech.init.recipes.OritechRecipe")) {
            try {
                List<?> outputs = (List<?>) recipe.getClass().getMethod("getResults").invoke(recipe);
                return outputs.isEmpty() ? ItemStack.EMPTY : ((ItemStack) outputs.get(0)).copy();
            } catch (ReflectiveOperationException | RuntimeException failure) { return ItemStack.EMPTY; }
        }
        return recipe.getResultItem(registries);
    }

    public static List<Input> inputs(Recipe<?> recipe) {
        if (recipe.getClass().getName().equals("blusunrize.immersiveengineering.api.crafting.AlloyRecipe")) {
            try {
                List<Input> inputs = new ArrayList<>();
                for (String name : List.of("input0", "input1")) {
                    Object sized = recipe.getClass().getField(name).get(recipe);
                    inputs.add(new Input((Ingredient) sized.getClass().getMethod("getBaseIngredient").invoke(sized),
                            ((Number) sized.getClass().getMethod("getCount").invoke(sized)).intValue()));
                }
                return List.copyOf(inputs);
            } catch (ReflectiveOperationException | RuntimeException failure) { return List.of(); }
        }
        return recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).map(ingredient -> new Input(ingredient, 1)).toList();
    }

    public static boolean itemOnly(Recipe<?> recipe) {
        if (!recipe.getClass().getName().equals("rearth.oritech.init.recipes.OritechRecipe")) return true;
        try {
            Object fluid = recipe.getClass().getMethod("getFluidInput").invoke(recipe);
            if (fluid != null && ((Number) fluid.getClass().getMethod("amount").invoke(fluid)).longValue() > 0) return false;
            return ((List<?>) recipe.getClass().getMethod("getFluidOutputs").invoke(recipe)).isEmpty();
        } catch (ReflectiveOperationException | RuntimeException failure) { return false; }
    }
}
