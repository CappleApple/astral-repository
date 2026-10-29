package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.RecipeTransfers;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.menu.RecipeTransferPlan;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;

/** Opt-in comparison with the 1.11.13 client predicate, using real synchronized stock and viewer handlers. */
public final class RecipeAvailabilityPerformanceCheck {
    private RecipeAvailabilityPerformanceCheck() {}

    public static void verify(NexusScreen screen) throws Exception {
        var menu = screen.getMenu();
        var recipes = Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(r -> RecipeTransfers.supports(r.value())).limit(512).toList();
        check(recipes.size() >= 100, "Too few crafting recipes for performance comparison");
        verifyInvalidation();
        String viewer = ModList.get().isLoaded("emi") ? "EMI" : "JEI";
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("viewer", viewer);
        result.put("emiAndJeiInstalled", ModList.get().isLoaded("emi") && ModList.get().isLoaded("jei"));
        result.put("storedIdentities", menu.recipeInventory().size());
        result.put("recipesPerPass", recipes.size());
        Predicate<RecipeHolder<CraftingRecipe>> actual = viewer.equals("EMI") ? Emi.checker(screen, result) : Jei.checker(screen);
        Map<RecipeHolder<CraftingRecipe>,Boolean> expected = new IdentityHashMap<>();
        for (var recipe : recipes) expected.put(recipe, baselineCanFill(menu, recipe.value()));
        for (var recipe : recipes) check(actual.test(recipe) == expected.get(recipe), "Viewer availability differs from baseline: " + recipe.id());
        // Compare the same recipe set on the same thread and unchanged synchronized inventory.
        Measurement baseline = measure(() -> { for (var recipe : recipes) baselineCanFill(menu, recipe.value()); });
        Measurement cached = measure(() -> { for (var recipe : recipes) actual.test(recipe); });
        result.put("baseline113", baseline);
        result.put("cachedViewer", cached);
        result.put("elapsedReduction", (double) baseline.medianNanos() / Math.max(1, cached.medianNanos()));
        result.put("allocationReduction", baseline.medianAllocatedBytes() < 0 ? -1 :
                (double) baseline.medianAllocatedBytes() / Math.max(1, cached.medianAllocatedBytes()));
        check(cached.medianNanos() < baseline.medianNanos() / 3, "Unchanged viewer checks did not remove full-inventory work");
        if (baseline.medianAllocatedBytes() >= 0)
            check(cached.medianAllocatedBytes() < baseline.medianAllocatedBytes() / 3, "Unchanged viewer checks still allocate full inventories");
        String profile = viewer.equals("EMI") && ModList.get().isLoaded("jei") ? "emi-jei" : viewer.toLowerCase(Locale.ROOT);
        Path out = Path.of("../build/nexus-performance/runtime-" + profile + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    private static void verifyInvalidation() {
        var mc = Minecraft.getInstance();
        var menu = new NexusMenu(32000, new net.minecraft.world.entity.player.Inventory(mc.player));
        var recipe = (CraftingRecipe) mc.level.getRecipeManager().byKey(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("iron_trapdoor")).orElseThrow().value();
        var iron = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        var initial = RecipeTransfers.inventory(menu);
        menu.acceptRecipeInventory(new com.cappleapple.astralrepository.network.NetworkPackets.RecipeInventory(
                menu.containerId, 1, true, true, false, List.of(new NexusMenu.Entry(iron, 3, false))));
        check(RecipeTransfers.inventory(menu) == initial && !RecipeTransfers.canFill(menu, recipe), "Partial network revision became visible");
        menu.acceptRecipeInventory(new com.cappleapple.astralrepository.network.NetworkPackets.RecipeInventory(
                menu.containerId, 1, false, false, true, List.of()));
        check(RecipeTransfers.inventory(menu) != initial && !RecipeTransfers.canFill(menu, recipe), "Completed network revision did not invalidate missing ingredients");
        menu.slots.get(10).set(iron.copy());
        check(RecipeTransfers.canFill(menu, recipe), "Local inventory did not enable recipe");
        var beforeComponents = RecipeTransfers.inventory(menu);
        var named = menu.slots.get(10).getItem();
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("exact component"));
        check(RecipeTransfers.inventory(menu) != beforeComponents && RecipeTransfers.canFill(menu, recipe), "In-place component edit did not invalidate stock");
        named.setCount(0);
        check(!RecipeTransfers.canFill(menu, recipe), "In-place count edit left a stale positive result");
        menu.setCarried(iron.copy());
        check(RecipeTransfers.canFill(menu, recipe), "Cursor stock was omitted");
        check(RecipeTransfers.inventory(menu).get(new ItemKey(iron)) == 3, "Cursor was duplicated in EMI's constructor input");
        menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        check(!RecipeTransfers.canFill(menu, recipe), "Cursor removal left a stale positive result");
        menu.acceptRecipeInventory(new com.cappleapple.astralrepository.network.NetworkPackets.RecipeInventory(
                menu.containerId, 2, true, false, false, List.of(new NexusMenu.Entry(iron, 4, false))));
        check(!RecipeTransfers.canFill(menu, recipe), "Partial delta changed availability");
        menu.acceptRecipeInventory(new com.cappleapple.astralrepository.network.NetworkPackets.RecipeInventory(
                menu.containerId, 2, false, false, true, List.of()));
        check(RecipeTransfers.canFill(menu, recipe), "Committed count delta did not invalidate a negative result");
        menu.acceptRecipeInventory(new com.cappleapple.astralrepository.network.NetworkPackets.RecipeInventory(
                menu.containerId, 3, true, false, true, List.of(new NexusMenu.Entry(iron, 0, false))));
        check(!RecipeTransfers.canFill(menu, recipe), "Committed removal left a stale positive result");

        // Custom predicates can change without any inventory update and must retain direct matching.
        var selected = iron.copy();
        selected.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("exact component"));
        menu.slots.get(10).set(selected.copy());
        boolean[] enabled = { false };
        var custom = new net.neoforged.neoforge.common.crafting.ICustomIngredient() {
            @Override public boolean test(net.minecraft.world.item.ItemStack stack) {
                return enabled[0] && net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, selected);
            }
            @Override public java.util.stream.Stream<net.minecraft.world.item.ItemStack> getItems() { return java.util.stream.Stream.of(selected); }
            @Override public boolean isSimple() { return false; }
            @Override public net.neoforged.neoforge.common.crafting.IngredientType<?> getType() {
                throw new UnsupportedOperationException("Transient fixture ingredient is not serialized");
            }
        }.toVanilla();
        var dynamic = new net.minecraft.world.item.crafting.ShapelessRecipe("", net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT),
                net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY, custom));
        check(!RecipeTransfers.canFill(menu, dynamic), "Disabled custom predicate matched");
        enabled[0] = true;
        check(RecipeTransfers.canFill(menu, dynamic), "Changed custom predicate was incorrectly cached");
        menu.slots.get(10).getItem().remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        check(!RecipeTransfers.canFill(menu, dynamic), "Custom component predicate accepted the wrong identity");
    }

    private static boolean baselineCanFill(NexusMenu menu, CraftingRecipe recipe) {
        // This is the shipped 1.11.13 predicate, retained only as an opt-in benchmark baseline.
        if (!RecipeTransfers.supports(recipe)) return false;
        Map<ItemKey,Long> stock = menu.recipeInventory();
        if (!menu.getCarried().isEmpty()) stock.merge(new ItemKey(menu.getCarried()), (long) menu.getCarried().getCount(), Long::sum);
        var ingredients = NexusMenu.recipeIngredients(recipe, stock);
        return ingredients != null && RecipeTransferPlan.assign(ingredients, stock, 1, key -> key.sample().getMaxStackSize()) != null;
    }

    private record Measurement(long medianNanos, long medianAllocatedBytes, List<Long> nanos, List<Long> allocatedBytes) {}
    private static Measurement measure(Runnable action) {
        var management = ManagementFactory.getThreadMXBean();
        var bean = management instanceof com.sun.management.ThreadMXBean candidate && candidate.isThreadAllocatedMemorySupported() ? candidate : null;
        if (bean != null && !bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        List<Long> times = new ArrayList<>(), allocations = new ArrayList<>();
        for (int sample = 0; sample < 3; sample++) {
            long before = bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            long start = System.nanoTime(); action.run(); times.add(System.nanoTime() - start);
            allocations.add(bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before);
        }
        List<Long> sortedTimes = times.stream().sorted().toList(), sortedAllocations = allocations.stream().sorted().toList();
        return new Measurement(sortedTimes.get(1), sortedAllocations.get(1), times, allocations);
    }

    private static final class Emi {
        static Predicate<RecipeHolder<CraftingRecipe>> checker(NexusScreen screen, Map<String,Object> result) {
            var handler = new com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler();
            var inventory = handler.getInventory(screen);
            for (int i = 0; i < 32; i++) {
                var unchanged = handler.getInventory(screen);
                check(inventory == unchanged && inventory.isEqual(unchanged), "EMI rebuilt an unchanged inventory");
            }
            long start = System.nanoTime();
            result.put("actualEmiCraftableOutputs", inventory.getCraftables().size());
            result.put("actualEmiGetCraftablesNanos", System.nanoTime() - start);
            Map<net.minecraft.resources.ResourceLocation, dev.emi.emi.api.recipe.EmiRecipe> indexed = new HashMap<>();
            for (var recipe : dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes())
                if (recipe.getBackingRecipe() != null) indexed.putIfAbsent(recipe.getBackingRecipe().id(), recipe);
            var context = new dev.emi.emi.api.recipe.handler.EmiCraftContext<>(screen, inventory,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1);
            return holder -> {
                var recipe = indexed.get(holder.id());
                check(recipe != null, "EMI did not expose fixture recipe: " + holder.id());
                return handler.canCraft(recipe, context);
            };
        }
    }
    private static final class Jei {
        static Predicate<RecipeHolder<CraftingRecipe>> checker(NexusScreen screen) throws Exception {
            // Obtain JEI's actual registered transfer handler rather than constructing a test substitute.
            var field = com.cappleapple.astralrepository.compat.AstralJeiPlugin.class.getDeclaredField("runtime");
            field.setAccessible(true);
            var runtime = (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
            var category = runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category).orElseThrow();
            return recipe -> handler.transferRecipe(screen.getMenu(), recipe, null, Minecraft.getInstance().player, false, false) == null;
        }
    }
    private static void check(boolean success, String message) { if (!success) throw new AssertionError(message); }
}
