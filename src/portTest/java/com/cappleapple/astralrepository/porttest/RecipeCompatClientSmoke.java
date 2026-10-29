package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.RecipeTransfers;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.CrystalNodeBlockEntity;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.cappleapple.astralrepository.network.NetworkPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Real client/server transfer check; run separately with EMI and standalone JEI. */
public final class RecipeCompatClientSmoke {
    private static final BlockPos NEXUS = new BlockPos(26, -59, 14), STORE = NEXUS.east(2);
    private static final ResourceLocation RECIPE = new ResourceLocation("minecraft", "iron_trapdoor");
    private static final Path OUT = Path.of("recipe-captures");
    private static int phase, ticks;
    private static int cacheNetworkStep;
    private static java.util.Map<ItemKey,Long> cacheNetworkBefore;
    private static Object cacheViewerBefore;
    private static ItemStack cacheHeldIron = ItemStack.EMPTY;
    private static volatile boolean ready;
    private static volatile Throwable failure;

    public static boolean tick() throws Exception {
        var mc = Minecraft.getInstance();
        if (failure != null) throw new AssertionError("Recipe transfer server check", failure);
        if (++ticks > 900) throw new AssertionError("Recipe transfer timeout in phase " + phase);
        if (phase == 0) {
            phase = 1;
            server(p -> {
                p.closeContainer();
                var level = p.serverLevel();
                level.getChunkAt(NEXUS);
                level.setBlockAndUpdate(NEXUS, AstralContent.STORAGE_NEXUS.get().defaultBlockState());
                level.setBlockAndUpdate(STORE, AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
                ((CrystalNodeBlockEntity) level.getBlockEntity(NEXUS)).setChannel(12);
                var storage = (CrystalNodeBlockEntity) level.getBlockEntity(STORE);
                storage.setChannel(12);
                int slot = 0;
                for (var item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR || item == Items.IRON_INGOT) continue;
                    check(storage.inventory().insertItem(slot++, new ItemStack(item), false).isEmpty(), "Fixture storage rejected item");
                    if (slot == 80) break;
                }
                check(storage.inventory().insertItem(slot, new ItemStack(Items.IRON_INGOT, 8), false).isEmpty(), "Fixture storage rejected iron");
                p.getInventory().clearContent();
                p.connection.teleport(27.5, -59, 17, 180, 20);
            });
        } else if (phase == 1 && ready && ticks > 100) {
            ready = false;
            server(p -> NetworkManager.open(p, GlobalPos.of(p.level().dimension(), NEXUS)));
            next(2);
        } else if (phase == 2 && mc.screen instanceof NexusScreen screen && ticks > 20) {
            if (!viewerReady()) return false;
            RecipeTransfers.inventory(screen.getMenu());
            var search = (net.minecraft.client.gui.components.EditBox) screen.children().stream()
                    .filter(w -> w instanceof net.minecraft.client.gui.components.EditBox e && e.getMessage().getString().equals("Search storage")).findFirst().orElseThrow();
            search.setValue("diamond");
            next(3);
        } else if (phase == 3 && mc.screen instanceof NexusScreen screen && ticks > 20) {
            var stock = screen.getMenu().recipeInventory();
            if (cacheNetworkStep == 0) {
                if (stock.size() < 81) return false;
                check(stock.getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 8, "Off-page iron is absent from recipe availability");
            }
            check(screen.getMenu().entries.stream().noneMatch(e -> e.stack().is(Items.IRON_INGOT)), "Search did not hide the ingredient");
            if (!checkCacheNetworkChanges(screen)) return false;
            if (ModList.get().isLoaded("emi")) Emi.fill(screen);
            else if (ModList.get().isLoaded("jei")) Jei.fill(screen);
            else throw new AssertionError("Recipe viewer fixture needs EMI or JEI");
            next(4);
        } else if (phase == 4 && mc.screen instanceof NexusScreen screen && ticks > 20) {
            if (!screen.getMenu().slots.get(0).getItem().is(Items.IRON_TRAPDOOR)) return false;
            check(screen.getMenu().grid.getItem(0).is(Items.IRON_INGOT)
                    && screen.getMenu().grid.getItem(1).is(Items.IRON_INGOT)
                    && screen.getMenu().grid.getItem(3).is(Items.IRON_INGOT)
                    && screen.getMenu().grid.getItem(4).is(Items.IRON_INGOT), "Transferred shaped grid has wrong positions");
            check(screen.getMenu().grid.getItem(2).isEmpty(), "Transfer filled an unused shaped position");
            if (screen.getMenu().recipeInventory().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) != 8) return false;
            shot("recipe_transfer_" + viewer() + ".png");
            ready = false;
            server(p -> {
                var menu = (com.cappleapple.astralrepository.menu.NexusMenu) p.containerMenu;
                check(menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 4, "Transfer did not consume exactly four stored iron");
                check(p.getInventory().isEmpty() && menu.getCarried().isEmpty(), "Transfer unexpectedly used player inventory or cursor");
            });
            next(5);
        } else if (phase == 5 && ready) {
            if (ModList.get().isLoaded("polymorph")) {
                ready = false;
                server(p -> {
                    var menu = (com.cappleapple.astralrepository.menu.NexusMenu) p.containerMenu;
                    menu.action(new NetworkPackets.Action(menu.containerId, NetworkPackets.CLEAR_GRID, ItemStack.EMPTY, 0, 0, ""));
                    menu.grid.setItem(0, new ItemStack(Items.DANDELION));
                    menu.slotsChanged(menu.grid);
                });
                next(6);
                return false;
            }
            return finish();
        } else if (phase == 6 && ready && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            check(!screen.getMenu().slots.get(0).getItem().isEmpty(), "Conflicting crafting recipe has no initial result");
            selectPolymorph(screen, "conflict_crafting_a");
            next(7);
        } else if (phase == 7 && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            if (!screen.getMenu().slots.get(0).getItem().is(Items.GOLD_NUGGET)) return false;
            shot("polymorph_gold_" + viewer() + ".png");
            selectPolymorph(screen, "conflict_crafting_z");
            next(8);
        } else if (phase == 8 && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            if (!screen.getMenu().slots.get(0).getItem().is(Items.IRON_NUGGET)) return false;
            check(screen.getMenu().grid.getItem(0).is(Items.DANDELION), "Polymorph selection consumed the ingredient");
            shot("polymorph_iron_" + viewer() + ".png");
            return finish();
        }
        return false;
    }

    private static boolean checkCacheNetworkChanges(NexusScreen screen) throws Exception {
        var menu = screen.getMenu();
        var iron = new ItemKey(new ItemStack(Items.IRON_INGOT));
        var recipe = (CraftingRecipe) Minecraft.getInstance().level.getRecipeManager().byKey(RECIPE).orElseThrow();
        if (cacheNetworkStep == 0) {
            var initial = RecipeTransfers.inventory(menu);
            for (int repeat = 0; repeat < 200; repeat++)
                check(RecipeTransfers.inventory(menu) == initial && RecipeTransfers.canFill(menu, recipe), "Stable checks rebuilt or changed client stock");
            try { initial.put(iron, 0L); throw new AssertionError("Cached stock must be immutable"); }
            catch (UnsupportedOperationException expected) {}
            menu.grid.setItem(0, new ItemStack(Items.IRON_INGOT));
            var localOne = RecipeTransfers.inventory(menu);
            check(localOne != initial && localOne.get(iron) == 9, "Local stack did not invalidate stock");
            Object first = viewerInventory(screen);
            menu.grid.getItem(0).setCount(2);
            var localTwo = RecipeTransfers.inventory(menu);
            check(localTwo != localOne && localTwo.get(iron) == 10, "In-place local count did not invalidate stock");
            changedViewer(screen, first, "local count");
            first = viewerInventory(screen);
            menu.grid.getItem(0).setHoverName(net.minecraft.network.chat.Component.literal("cache probe"));
            var named = new ItemKey(menu.grid.getItem(0));
            var localNamed = RecipeTransfers.inventory(menu);
            check(localNamed != localTwo && localNamed.get(iron) == 8 && localNamed.get(named) == 2,
                    "In-place local NBT was merged with another exact identity");
            changedViewer(screen, first, "local NBT");
            checkCustomPredicate(menu, menu.grid.getItem(0).copy());
            menu.grid.setItem(0, ItemStack.EMPTY);
            menu.setCarried(new ItemStack(Items.IRON_INGOT));
            var cursorOne = RecipeTransfers.inventory(menu);
            check(cursorOne.get(iron) == 8, "Cursor was double-counted in the viewer input stock");
            first = viewerInventory(screen);
            menu.getCarried().setCount(2);
            var cursorTwo = RecipeTransfers.inventory(menu);
            check(cursorTwo != cursorOne && cursorTwo.get(iron) == 8, "In-place cursor count did not invalidate stock");
            changedViewer(screen, first, "cursor count");
            first = viewerInventory(screen);
            menu.getCarried().setHoverName(net.minecraft.network.chat.Component.literal("cursor cache probe"));
            check(RecipeTransfers.inventory(menu) != cursorTwo, "In-place cursor NBT did not invalidate stock");
            changedViewer(screen, first, "cursor NBT");
            menu.setCarried(ItemStack.EMPTY);
            cacheNetworkBefore = RecipeTransfers.inventory(menu);
            cacheViewerBefore = viewerInventory(screen);
            cacheNetworkStep = 1;
            server(p -> {
                var storage = (CrystalNodeBlockEntity) p.serverLevel().getBlockEntity(STORE);
                for (int slot = 0; slot < storage.inventory().getSlots(); slot++)
                    if (storage.inventory().getStackInSlot(slot).is(Items.IRON_INGOT)) {
                        cacheHeldIron = storage.inventory().extractItem(slot, 8, false);
                        break;
                    }
                check(cacheHeldIron.is(Items.IRON_INGOT) && cacheHeldIron.getCount() == 8, "Fixture did not retain eight real iron");
            });
            return false;
        }
        if (cacheNetworkStep == 1) {
            var current = RecipeTransfers.inventory(menu);
            if (current.getOrDefault(iron, 0L) != 0) return false;
            check(current != cacheNetworkBefore && cacheNetworkBefore.get(iron) == 8, "Committed removal did not replace a detached snapshot");
            check(!RecipeTransfers.canFill(menu, recipe), "Removed real ingredients stayed craftable");
            changedViewer(screen, cacheViewerBefore, "committed network removal");
            menu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
            check(!RecipeTransfers.canFill(menu, recipe), "Three cursor iron were counted twice for a four-iron recipe");
            var cursorThree = RecipeTransfers.inventory(menu);
            check(cursorThree.getOrDefault(iron, 0L) == 0, "Cursor leaked into viewer constructor input");
            menu.getCarried().setCount(4);
            check(RecipeTransfers.canFill(menu, recipe) && RecipeTransfers.inventory(menu) != cursorThree,
                    "Cursor count edit retained a stale negative result");
            menu.setCarried(ItemStack.EMPTY);
            check(!RecipeTransfers.canFill(menu, recipe), "Cursor removal retained a stale positive result");
            cacheNetworkBefore = RecipeTransfers.inventory(menu);
            cacheViewerBefore = viewerInventory(screen);
            cacheNetworkStep = 2;
            server(p -> {
                var storage = (CrystalNodeBlockEntity) p.serverLevel().getBlockEntity(STORE);
                check(net.minecraftforge.items.ItemHandlerHelper.insertItem(storage.inventory(), cacheHeldIron, false).isEmpty(),
                        "Fixture could not restore the original iron");
                cacheHeldIron = ItemStack.EMPTY;
            });
            return false;
        }
        if (cacheNetworkStep == 2) {
            var current = RecipeTransfers.inventory(menu);
            if (current.getOrDefault(iron, 0L) != 8) return false;
            check(current != cacheNetworkBefore && RecipeTransfers.canFill(menu, recipe), "Restored real ingredients stayed uncraftable");
            changedViewer(screen, cacheViewerBefore, "committed network restoration");
            cacheNetworkStep = 3;
        }
        return true;
    }

    private static void checkCustomPredicate(com.cappleapple.astralrepository.menu.NexusMenu menu, ItemStack selected) {
        boolean[] enabled = { false };
        var ingredient = new net.minecraft.world.item.crafting.Ingredient(
                java.util.stream.Stream.of(new net.minecraft.world.item.crafting.Ingredient.ItemValue(selected))) {
            @Override public boolean test(ItemStack stack) { return enabled[0] && ItemStack.isSameItemSameTags(stack, selected); }
            @Override public boolean isSimple() { return false; }
        };
        var recipe = new net.minecraft.world.item.crafting.ShapelessRecipe(new ResourceLocation("astral_repository_recipe_test", "dynamic_cache"),
                "", net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                new ItemStack(Items.GOLD_INGOT), net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY, ingredient));
        check(!RecipeTransfers.canFill(menu, recipe), "Disabled custom predicate matched");
        enabled[0] = true;
        check(RecipeTransfers.canFill(menu, recipe), "Custom predicate change was cached without an inventory change");
        menu.grid.getItem(0).resetHoverName();
        check(!RecipeTransfers.canFill(menu, recipe), "Custom NBT predicate accepted another exact identity");
    }

    private static Object viewerInventory(NexusScreen screen) throws Exception {
        return ModList.get().isLoaded("emi") ? Emi.handler(screen).getInventory(screen) : null;
    }

    private static void changedViewer(NexusScreen screen, Object before, String reason) throws Exception {
        if (before != null) check(viewerInventory(screen) != before, "EMI retained stale inventory after " + reason);
    }

    private static boolean finish() throws Exception {
            var mc = Minecraft.getInstance();
            check(!mc.mouseHandler.isMouseGrabbed(), "Test client grabbed the mouse");
            check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0, "Test client audio is not muted");
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.txt"), "PASS: " + viewer() + " availability includes 81 stored identities beyond the 54-entry page; hidden iron fills an actual shaped recipe through the server packet, preserving all eight iron across storage/grid."
                    + " Stable inventory/viewer reuse, in-place count/NBT/cursor edits, cursor capacity, dynamic custom predicate, and real network removal/restoration invalidation pass."
                    + (ModList.get().isLoaded("polymorph") ? " Polymorph's actual Nexus widget selects both conflicting outputs without consuming ingredients." : "") + "\n");
            return true;
    }

    private static void selectPolymorph(NexusScreen screen, String recipe) throws Exception {
        var widgetType = Class.forName("com.illusivesoulworks.polymorph.client.recipe.RecipesWidget");
        Object widget = ((java.util.Optional<?>)widgetType.getMethod("get").invoke(null)).orElseThrow();
        var api = Class.forName("com.illusivesoulworks.polymorph.api.client.base.IRecipesWidget");
        check(api.getMethod("getOutputSlot").invoke(widget) == screen.getMenu().slots.get(0), "Polymorph targets the wrong Nexus result slot");
        api.getMethod("selectRecipe", ResourceLocation.class).invoke(widget, new ResourceLocation("astral_repository_recipe_test", recipe));
    }

    private static boolean viewerReady() throws Exception {
        if (ModList.get().isLoaded("emi")) return com.cappleapple.astralrepository.compat.AstralEmiPlugin.registered
                && !dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().isEmpty();
        if (!ModList.get().isLoaded("jei")) throw new AssertionError("Recipe viewer not installed");
        return Jei.runtime() != null;
    }

    private static final class Emi {
        static com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler handler(NexusScreen screen) {
            com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler handler;
            try {
                var handlers = (java.util.List<?>)Class.forName("dev.emi.emi.registry.EmiRecipeFiller").getMethod("getAllHandlers", net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class).invoke(null, screen);
                handler = (com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler)handlers.stream().filter(com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler.class::isInstance).findFirst().orElseThrow();
            } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect registered EMI handlers", failure); }
            return handler;
        }
        static void fill(NexusScreen screen) {
            var handler = handler(screen);
            var recipe = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().stream()
                    .filter(r -> r.getBackingRecipe() != null && r.getBackingRecipe().getId().equals(RECIPE)).findFirst().orElseThrow();
            var inventory = handler.getInventory(screen);
            var context = new dev.emi.emi.api.recipe.handler.EmiCraftContext<>(screen, inventory,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1);
            check(handler.supportsRecipe(recipe) && handler.canCraft(recipe, context), "EMI disabled quick fill despite available network ingredients");
            for (int repeat = 0; repeat < 200; repeat++)
                check(handler.getInventory(screen) == inventory && inventory.isEqual(inventory) && handler.canCraft(recipe, context),
                        "Registered EMI handler rebuilt stable inventory or changed availability");
            check(handler.craft(recipe, context), "EMI quick-fill handler rejected recipe");
        }
    }

    private static final class Jei {
        static mezz.jei.api.runtime.IJeiRuntime runtime() throws Exception {
            var field = com.cappleapple.astralrepository.compat.AstralJeiPlugin.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
        }

        static void fill(NexusScreen screen) throws Exception {
            var runtime = runtime();
            var category = runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category).orElseThrow();
            check(handler instanceof com.cappleapple.astralrepository.compat.AstralJeiPlugin.RecipeHandler, "JEI did not register the Nexus transfer handler");
            var recipe = (CraftingRecipe) Minecraft.getInstance().level.getRecipeManager().byKey(RECIPE).orElseThrow();
            check(handler.transferRecipe(screen.getMenu(), recipe, null, Minecraft.getInstance().player, false, false) == null,
                    "JEI disabled quick fill despite available network ingredients");
            for (int repeat = 0; repeat < 200; repeat++)
                check(handler.transferRecipe(screen.getMenu(), recipe, null, Minecraft.getInstance().player, false, false) == null,
                        "Registered JEI handler changed stable availability");
            check(handler.transferRecipe(screen.getMenu(), recipe, null, Minecraft.getInstance().player, false, true) == null,
                    "JEI quick-fill handler rejected recipe");
        }
    }

    private static String viewer() { return ModList.get().isLoaded("emi") ? "EMI" : "JEI"; }
    private static void next(int value) { phase = value; ticks = 0; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())); ready = true; }
            catch (Throwable error) { failure = error; }
        });
    }
    private static void shot(String name) throws Exception {
        Files.createDirectories(OUT);
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(OUT.resolve(name)); }
    }
}
