package com.cappleapple.astralrepository.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import java.lang.reflect.*;
import java.util.*;

/** Discovers the installed Mekanism recipe contracts rather than a list of machine blocks. */
public final class MekanismRecipeDiscovery {
    private record IngredientMethods(Method amount, Method accepts) {}
    private static final ClassValue<IngredientMethods> INGREDIENT_METHODS = new ClassValue<>() {
        @Override protected IngredientMethods computeValue(Class<?> type) {
            try {
                Class<?> api = Class.forName("mekanism.api.recipes.ingredients.InputIngredient", false, type.getClassLoader());
                return new IngredientMethods(api.getMethod("getNeededAmount", Object.class), api.getMethod("testType", Object.class));
            } catch (ReflectiveOperationException unavailable) { throw new IllegalStateException("Unsupported native input ingredient contract", unavailable); }
        }
    };
    public record Entry(String type, String id, Object recipe) {}
    public record Input(String name, String resource, Object ingredient, List<?> representations) {
        public long amount(Object sample) { return ((Number) apply(INGREDIENT_METHODS.get(ingredient.getClass()).amount(), ingredient, sample)).longValue(); }
        public boolean accepts(Object sample) {
            if (!representations.isEmpty()) {
                Object reference = representations.get(0);
                if (!reference.getClass().isInstance(sample) && !sample.getClass().isInstance(reference)) return false;
            }
            return Boolean.TRUE.equals(apply(INGREDIENT_METHODS.get(ingredient.getClass()).accepts(), ingredient, sample));
        }
    }
    private static Object apply(Method method, Object ingredient, Object sample) {
        try { return method.invoke(ingredient, sample); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native input ingredient rejected its resource contract", failure); }
    }
    private MekanismRecipeDiscovery() {}

    public static List<Entry> recipes(ServerLevel level) {
        return recipes(level, List.of());
    }
    public static List<Entry> recipes(ServerLevel level, List<?> handlers) {
        Map<String, Entry> result = new LinkedHashMap<>();
        Set<Object> providers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object type : BuiltInRegistries.RECIPE_TYPE) if (implementsContract(type.getClass(), "mekanism.common.recipe.IMekanismRecipeTypeProvider")) providers.add(type);
        for (Object handler : handlers) if (hasMethod(handler, "getRecipeType")) providers.add(invoke(handler, "getRecipeType"));
        for (Object type : providers) {
            if (!implementsContract(type.getClass(), "mekanism.common.recipe.IMekanismRecipeTypeProvider")) continue;
            try {
                String name = invoke(type, "getRegistryName").toString();
                for (Object holder : (List<?>) invoke(type, "getRecipes", new Class<?>[]{net.minecraft.world.level.Level.class}, level)) {
                    if (holder instanceof Recipe<?> recipe) result.putIfAbsent(name + "/" + recipe.getId(), new Entry(name, recipe.getId().toString(), recipe));
                }
            } catch (RuntimeException unavailable) { CraftingService.LOGGER.warn("Mekanism recipe provider unavailable: {}", type, unavailable); }
        }
        return List.copyOf(result.values());
    }

    public static List<Input> inputs(Object recipe) {
        List<Input> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Method method : Arrays.stream(recipe.getClass().getMethods()).sorted(Comparator.comparingInt((Method method) -> inputOrder(method.getName())).thenComparing(Method::getName)).toList()) {
            if (method.isBridge() || method.getParameterCount() != 0 || !method.getName().startsWith("get")
                    || !implementsContract(method.getReturnType(), "mekanism.api.recipes.ingredients.InputIngredient")) continue;
            String name = method.getName();
            // Basic dual-direction recipes expose Raw serialization aliases as well as
            // validated logical getters. These aliases are not additional ingredients.
            if (name.endsWith("Raw") && hasMethod(recipe, name.substring(0, name.length() - 3))) continue;
            if (name.equals("getFluidInput")) {
                String direction = hasMethod(recipe, "hasFluidToChemical") ? "hasFluidToChemical" : hasMethod(recipe, "hasFluidToGas") ? "hasFluidToGas" : null;
                if (direction != null && !Boolean.TRUE.equals(invoke(recipe, direction))) continue;
            }
            if (name.equals("getChemicalInput") || name.equals("getGasInput")) {
                String direction = hasMethod(recipe, "hasChemicalToFluid") ? "hasChemicalToFluid" : hasMethod(recipe, "hasGasToFluid") ? "hasGasToFluid" : null;
                if (direction != null && !Boolean.TRUE.equals(invoke(recipe, direction))) continue;
            }
            Object ingredient = invoke(recipe, method.getName());
            if (ingredient == null || !seen.add(method.getName())) continue;
            List<?> representations = (List<?>) invoke(ingredient, "getRepresentations");
            String kind = representations.isEmpty() ? "unknown" : resource(representations.get(0));
            result.add(new Input(method.getName(), kind, ingredient, List.copyOf(representations)));
        }
        return List.copyOf(result);
    }
    private static int inputOrder(String name) {
        if (name.contains("Main") || name.contains("Left") || name.contains("Solid") || name.contains("Item")) return 0;
        if (name.contains("Fluid")) return 1;
        if (name.contains("Chemical") || name.contains("Gas") || name.contains("Infusion")) return 2;
        if (name.contains("Extra") || name.contains("Right")) return 3;
        return 0;
    }

    public static List<ItemStack> itemOutputs(Object recipe, String method) {
        if (!hasMethod(recipe, method)) return List.of();
        Object result = invoke(recipe, method);
        if (!(result instanceof List<?> values)) return List.of();
        return values.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    public static String resource(Object sample) {
        if (sample instanceof ItemStack) return "item";
        String name = sample.getClass().getName();
        if (name.contains(".fluids.") && name.endsWith("FluidStack")) return "fluid";
        if (name.startsWith("mekanism.api.chemical.")) return "chemical";
        return "unknown";
    }

    public static boolean implementsContract(Class<?> type, String name) {
        if (type == null) return false;
        if (type.getName().equals(name)) return true;
        for (Class<?> api : type.getInterfaces()) if (implementsContract(api, name)) return true;
        return implementsContract(type.getSuperclass(), name);
    }
    public static boolean hasMethod(Object value, String name) {
        // Enumerating every method on a tile resolves optional client-only signature types
        // on dedicated servers. Resolve only the server contract being requested.
        try { value.getClass().getMethod(name); return true; }
        catch (NoSuchMethodException absent) { return false; }
        catch (LinkageError invalidServerSignature) { CraftingService.LOGGER.error("Mekanism server signature lookup failed: {}.{}", value.getClass().getName(), name, invalidServerSignature); throw invalidServerSignature; }
    }
    public static Object invoke(Object target, String method) { return invoke(target, method, new Class<?>[]{}); }
    public static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try { Method method = target.getClass().getMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Mekanism contract " + target.getClass().getName() + "." + name, failure); }
        catch (LinkageError invalidServerSignature) { CraftingService.LOGGER.error("Mekanism server invocation lookup failed: {}.{}", target.getClass().getName(), name, invalidServerSignature); throw invalidServerSignature; }
    }
    public static Object callCompatible(Object target, String name, Object... args) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length || method.isBridge()) continue;
            Class<?>[] types = method.getParameterTypes(); boolean match = true;
            for (int i = 0; i < args.length; i++) if (args[i] != null && !types[i].isInstance(args[i])) { match = false; break; }
            if (match) return invoke(target, name, types, args);
        }
        throw new IllegalStateException("Missing Mekanism method " + name + " on " + target.getClass().getName());
    }
}
