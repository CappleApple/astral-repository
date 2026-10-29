package com.cappleapple.astralrepository.gametest;

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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Real client/server transfer check; run separately with EMI and standalone JEI. */
public final class RecipeCompatClientSmoke {
    private static final BlockPos NEXUS = new BlockPos(26, -59, 14), STORE = NEXUS.east(2);
    private static final ResourceLocation RECIPE = ResourceLocation.withDefaultNamespace("iron_trapdoor");
    private static final Path OUT = Path.of("../build/client-smoke");
    private static int phase, ticks;
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
                if (Boolean.getBoolean("astral_repository.recipePerformanceTest")) {
                    slot++;
                    for (int i = 0; i < Integer.getInteger("astral_repository.recipePerformanceIdentities", 10000); i++) {
                        ItemStack variant = new ItemStack(Items.STONE);
                        variant.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                                net.minecraft.network.chat.Component.literal("Recipe availability " + i));
                        check(storage.inventory().insertItem(slot++, variant, false).isEmpty(), "Fixture storage rejected component identity " + i);
                    }
                }
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
            if (stock.size() < 81) return false;
            if (Boolean.getBoolean("astral_repository.recipePerformanceTest")) {
                if (stock.size() < 81 + Integer.getInteger("astral_repository.recipePerformanceIdentities", 10000)) return false;
                RecipeAvailabilityPerformanceCheck.verify(screen);
            }
            check(stock.getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 8, "Off-page iron is absent from recipe availability");
            check(screen.getMenu().entries.stream().noneMatch(e -> e.stack().is(Items.IRON_INGOT)), "Search did not hide the ingredient");
            if (ModList.get().isLoaded("emi")) Emi.fill(screen);
            else if (ModList.get().isLoaded("jei")) Jei.fill(screen);
            else throw new AssertionError("Recipe viewer fixture needs EMI or JEI");
            next(4);
        } else if (phase == 4 && mc.screen instanceof NexusScreen screen && ticks > 20) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.IRON_TRAPDOOR)) return false;
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
            check(!screen.getMenu().slots.getFirst().getItem().isEmpty(), "Conflicting crafting recipe has no initial result");
            selectPolymorph(screen, "conflict_crafting_a");
            next(7);
        } else if (phase == 7 && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.GOLD_NUGGET)) return false;
            shot("polymorph_gold_" + viewer() + ".png");
            selectPolymorph(screen, "conflict_crafting_z");
            next(8);
        } else if (phase == 8 && ticks > 20 && mc.screen instanceof NexusScreen screen) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.IRON_NUGGET)) return false;
            check(screen.getMenu().grid.getItem(0).is(Items.DANDELION), "Polymorph selection consumed the ingredient");
            shot("polymorph_iron_" + viewer() + ".png");
            return finish();
        }
        return false;
    }

    private static boolean finish() throws Exception {
            var mc = Minecraft.getInstance();
            check(!mc.mouseHandler.isMouseGrabbed(), "Test client grabbed the mouse");
            check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0, "Test client audio is not muted");
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.txt"), "PASS: " + viewer() + " availability includes 81 stored identities beyond the 54-entry page; hidden iron fills an actual shaped recipe through the server packet, preserving all eight iron across storage/grid."
                    + (ModList.get().isLoaded("polymorph") ? " Polymorph's actual Nexus widget selects both conflicting outputs without consuming ingredients." : "") + "\n");
            return true;
    }

    private static void selectPolymorph(NexusScreen screen, String recipe) throws Exception {
        var type = Class.forName("com.illusivesoulworks.polymorph.api.client.PolymorphWidgets");
        Object widgets = type.getMethod("getInstance").invoke(null);
        Object widget = type.getMethod("getWidgetOrDefault", net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class).invoke(widgets, screen);
        check(widget != null, "Polymorph did not discover the Nexus crafting result");
        var api = Class.forName("com.illusivesoulworks.polymorph.api.client.base.IRecipesWidget");
        check(api.getMethod("getOutputSlot").invoke(widget) == screen.getMenu().slots.getFirst(), "Polymorph targets the wrong Nexus result slot");
        api.getMethod("selectRecipe", ResourceLocation.class).invoke(widget, ResourceLocation.fromNamespaceAndPath("astral_repository_recipe_test", recipe));
    }

    private static boolean viewerReady() throws Exception {
        if (ModList.get().isLoaded("emi")) return com.cappleapple.astralrepository.compat.AstralEmiPlugin.registered
                && !dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().isEmpty();
        if (!ModList.get().isLoaded("jei")) throw new AssertionError("Recipe viewer not installed");
        return Jei.runtime() != null;
    }

    private static final class Emi {
        static void fill(NexusScreen screen) {
            var handler = new com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler();
            var recipe = dev.emi.emi.api.EmiApi.getRecipeManager().getRecipes().stream()
                    .filter(r -> r.getBackingRecipe() != null && r.getBackingRecipe().id().equals(RECIPE)).findFirst().orElseThrow();
            var inventory = handler.getInventory(screen);
            var context = new dev.emi.emi.api.recipe.handler.EmiCraftContext<>(screen, inventory,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON,
                    dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE, 1);
            check(handler.supportsRecipe(recipe) && handler.canCraft(recipe, context), "EMI disabled quick fill despite available network ingredients");
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
            @SuppressWarnings("unchecked") var recipe = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) Minecraft.getInstance().level.getRecipeManager().byKey(RECIPE).orElseThrow();
            check(handler.transferRecipe(screen.getMenu(), recipe, null, Minecraft.getInstance().player, false, false) == null,
                    "JEI disabled quick fill despite available network ingredients");
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
