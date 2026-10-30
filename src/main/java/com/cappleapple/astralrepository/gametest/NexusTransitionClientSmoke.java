package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.client.RecipeTransfers;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.CrystalNodeBlockEntity;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.menu.RecipeTransferPlan;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.cappleapple.astralrepository.network.NetworkPackets;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Opt-in cold-menu/custom-catalogue checks. All resources and this class are excluded from releases. */
@EventBusSubscriber(modid = "astral_repository", value = Dist.CLIENT)
public final class NexusTransitionClientSmoke {
    private static final BlockPos AT = new BlockPos(26, -59, 14);
    private static final Path OUT = Path.of(System.getProperty("astral_repository.transitionOutput", "../build/nexus-transitions/runtime/default"));
    private static final Map<String,Object> report = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String,List<Long>> frames = new LinkedHashMap<>();
    private static final List<Map<String,Object>> opens = new ArrayList<>();
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static int phase, age, opening;
    private static NexusMenu lastMenu;
    private static NexusScreen screen;
    private static RecipeHolder<CraftingRecipe> selected;
    private static long openRequested, frameStart;
    private static String span = "setup", frameSpan;
    private static boolean skipFrame;

    @SubscribeEvent public static void frameStart(RenderFrameEvent.Pre event) {
        if (!Boolean.getBoolean("astral_repository.nexusTransitionTest")) return;
        frameStart = System.nanoTime(); frameSpan = span;
    }
    @SubscribeEvent public static void frameEnd(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("astral_repository.nexusTransitionTest")) return;
        if (skipFrame) { skipFrame = false; return; }
        if (frameStart != 0 && frameSpan != null && !frameSpan.equals("setup") && !frameSpan.equals("benchmark")) {
            List<Long> samples = frames.computeIfAbsent(frameSpan, ignored -> new ArrayList<>());
            if (samples.size() < 4096) samples.add(System.nanoTime() - frameStart);
        }
    }

    public static boolean tick() throws Exception {
        var mc = Minecraft.getInstance();
        if (failure != null) throw new AssertionError("Transition server assertion", failure);
        if (++age > 1600) throw new AssertionError("Transition fixture timeout in phase " + phase);
        if (phase == 0) {
            server(p -> {
                p.closeContainer(); p.getInventory().clearContent();
                var level = p.serverLevel(); level.setBlockAndUpdate(AT, AstralContent.STORAGE_NEXUS.get().defaultBlockState());
                ((CrystalNodeBlockEntity) level.getBlockEntity(AT)).setChannel(14);
                List<CrystalNodeBlockEntity> stores = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    BlockPos pos = AT.east(2 + i * 2); level.setBlockAndUpdate(pos, AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState());
                    var node = (CrystalNodeBlockEntity) level.getBlockEntity(pos); node.setChannel(14); stores.add(node);
                }
                int index = 0, identities = 0, types = 0;
                List<ItemStack> items = new ArrayList<>();
                items.add(new ItemStack(Items.SPRUCE_PLANKS, 64)); identities++;
                for (var item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR || item == Items.SPRUCE_PLANKS || item == Items.STICK) continue;
                    ItemStack stack = new ItemStack(item);
                    if (!stores.getFirst().inventory().isItemValid(0, stack)) continue;
                    items.add(stack); identities++; types++;
                }
                int wanted = Integer.getInteger("astral_repository.transitionIdentities", 10000);
                for (int i = identities; i < wanted; i++) {
                    var stack = new ItemStack(Items.STONE); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Transition identity " + i));
                    items.add(stack);
                }
                for (ItemStack stack : items) {
                    ItemStack remaining = stack;
                    while (index < stores.size()) {
                        var inventory = stores.get(index).inventory();
                        ItemStack rest = inventory.insertItem(inventory.getSlots() - 1, remaining, false);
                        if (rest.isEmpty()) break;
                        remaining = rest;
                        index++;
                    }
                    check(index < stores.size(), "Four basic storage crystals could not hold fixture stock");
                }
                report.put("storedIdentities", items.size()); report.put("registeredItemTypes", types + 1);
                p.connection.teleport(27.5, -59, 17, 180, 20);
            });
            next(1, "setup");
        } else if (phase == 1 && ready && age > 100) {
            requestOpen();
        } else if (phase == 2 && mc.screen instanceof NexusScreen current && current.getMenu() != lastMenu) {
            RecipeTransfers.inventory(current.getMenu());
            if (current.getMenu().committedRecipeInventory().size() < ((Number) report.get("storedIdentities")).intValue()) return false;
            if (age < 30) return false;
            screen = current; lastMenu = current.getMenu();
            opens.add(Map.of("round", opening, "requestToFullStockNanos", System.nanoTime() - openRequested));
            if (++opening < 3) requestOpen(); else next(3, "benchmark");
        } else if (phase == 3 && age > 20) {
            skipFrame = true;
            var recipes = mc.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(r -> r.id().getNamespace().equals("astral_repository_transition_test")).toList();
            check(recipes.size() == Integer.getInteger("astral_repository.transitionRecipes", 4096), "Generated custom catalogue was not loaded");
            selected = recipes.getFirst();
            var ingredient = selected.value().getIngredients().getFirst();
            check(!ingredient.isSimple() && ingredient.test(new ItemStack(Items.SPRUCE_PLANKS)) && !ingredient.test(new ItemStack(Items.OAK_PLANKS)), "Actual Moonlight swap semantics differ");
            check(com.cappleapple.astralrepository.compat.MoonlightRecipeCompatibility.isItemOnly(ingredient), "Exact Moonlight adapter was not enabled");
            RecipeAvailabilityPerformanceCheck.verifyInvalidation();
            report.put("actualCustomRecipes", recipes.size());
            coldComparison(recipes.subList(0, Math.min(512, recipes.size())), screen.getMenu().committedRecipeInventory());
            next(4, "recipe_open");
        } else if (phase == 4 && age > 20) {
            long start = System.nanoTime();
            if (ModList.get().isLoaded("emi")) Emi.open(selected.id());
            report.put("recipeOpenCallNanos", System.nanoTime() - start);
            next(5, "recipe_open");
        } else if (phase == 5 && age > 20) {
            long start = System.nanoTime(); fill(1); report.put("singleFillCallNanos", System.nanoTime() - start);
            next(6, "single_fill");
        } else if (phase == 6 && mc.screen instanceof NexusScreen && age > 20) {
            if (!screen.getMenu().slots.getFirst().getItem().is(Items.STICK)) return false;
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 0, 0, ClickType.PICKUP, mc.player);
            next(7, "single_craft");
        } else if (phase == 7 && age > 20) {
            if (!screen.getMenu().getCarried().is(Items.STICK)) return false;
            server(p -> check(countSpruce((NexusMenu) p.containerMenu) == 63, "Single craft did not consume exactly one stored plank"));
            next(8, "count_delta");
        } else if (phase == 8 && ready && age > 20) {
            long start = System.nanoTime(); fill(64); report.put("maxFillCallNanos", System.nanoTime() - start);
            next(9, "max_fill");
        } else if (phase == 9 && age > 20) {
            if (screen.getMenu().grid.getItem(0).getCount() != 63) return false;
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 0, 0, ClickType.QUICK_MOVE, mc.player);
            next(10, "max_craft");
        } else if (phase == 10 && age > 30) {
            if (!screen.getMenu().grid.isEmpty()) return false;
            server(p -> {
                var menu = (NexusMenu) p.containerMenu;
                check(countSpruce(menu) == 0, "Maximum craft left or duplicated planks");
                long sticks = p.getInventory().countItem(Items.STICK) + (menu.getCarried().is(Items.STICK) ? menu.getCarried().getCount() : 0);
                check(sticks == 64, "Maximum craft did not conserve all64 inputs: " + sticks);
            });
            next(11, "settled");
        } else if (phase == 11 && ready && age > 20) {
            report.put("opens", opens); report.put("status", "PASS"); report.put("renderEventWindows", frameReport());
            report.put("renderEventScope", "RenderFrameEvent.Pre/Post spans exclude tick/input work; explicit UI call spans and streaming latency are reported separately");
            report.put("viewer", ModList.get().isLoaded("emi") ? (ModList.get().isLoaded("jei") ? "EMI+JEI" : "EMI") : "JEI");
            check(!mc.mouseHandler.isMouseGrabbed(), "Transition client grabbed mouse");
            check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0, "Transition client audio not muted");
            Files.createDirectories(OUT); Files.writeString(OUT.resolve("result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            Path sentinel = Path.of("../build/client-smoke/result.txt");
            Files.createDirectories(sentinel.getParent());
            Files.writeString(sentinel, "PASS: cold Moonlight catalogue, three real Nexus opens, registered viewer single/max fills and64-input/output conservation\n");
            return true;
        }
        return false;
    }

    private static void coldComparison(List<RecipeHolder<CraftingRecipe>> recipes, Map<ItemKey,Long> stock) {
        List<Long> oldNanos = new ArrayList<>(), fixedNanos = new ArrayList<>(), oldBytes = new ArrayList<>(), fixedBytes = new ArrayList<>();
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int sample = 0; sample < 3; sample++) {
            var menu = detached(stock, 31000 + sample);
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
            for (var holder : recipes) check(oldFallback(holder.value(), stock), "Retained old Moonlight predicate rejected fixture");
            oldNanos.add(System.nanoTime() - start); oldBytes.add(bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before);
            before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()); start = System.nanoTime();
            for (var holder : recipes) check(RecipeTransfers.canFill(menu, holder.value()), "Cold indexed Moonlight recipe rejected fixture");
            fixedNanos.add(System.nanoTime() - start); fixedBytes.add(bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before);
        }
        report.put("coldComparison", Map.of("recipes", recipes.size(), "oldFallbackNanos", oldNanos, "freshCacheNanos", fixedNanos,
                "oldFallbackAllocatedBytes", oldBytes, "freshCacheAllocatedBytes", fixedBytes,
                "scope", "Old1.11.14non-simple matcher excludes old snapshot construction; fixed pass includes a fresh menu/index and zero cached recipe answers"));
        check(median(fixedBytes) < median(oldBytes) / 3, "Cold matching still allocates fullstock copies per custom recipe");
        if (ModList.get().isLoaded("emi")) Emi.tagEpoch(detached(stock, 31004));
    }
    private static NexusMenu detached(Map<ItemKey,Long> stock, int id) {
        var menu = new NexusMenu(id, new Inventory(Minecraft.getInstance().player));
        List<NexusMenu.Entry> entries = stock.entrySet().stream().map(e -> new NexusMenu.Entry(e.getKey().sample(), e.getValue(), false)).toList();
        for (int start = 0; start < entries.size(); start += NetworkPackets.WINDOW_SIZE) {
            int end = Math.min(entries.size(), start + NetworkPackets.WINDOW_SIZE);
            menu.acceptRecipeInventory(new NetworkPackets.RecipeInventory(id, 1, start == 0, true, end == entries.size(), entries.subList(start, end)));
        }
        return menu;
    }
    private static boolean oldFallback(CraftingRecipe recipe, Map<ItemKey,Long> stock) {
        List<List<ItemKey>> ingredients = new ArrayList<>();
        for (var ingredient : recipe.getIngredients()) ingredients.add(stock.keySet().stream().filter(k -> ingredient.test(k.sample())).toList());
        return RecipeTransferPlan.assign(ingredients, stock, 1, k -> k.sample().getMaxStackSize()) != null;
    }
    private static long countSpruce(NexusMenu menu) {
        long count = menu.network().snapshot().getOrDefault(new ItemKey(new ItemStack(Items.SPRUCE_PLANKS)), 0L);
        for (int i = 1; i < menu.slots.size(); i++) if (menu.slots.get(i).getItem().is(Items.SPRUCE_PLANKS)) count += menu.slots.get(i).getItem().getCount();
        if (menu.getCarried().is(Items.SPRUCE_PLANKS)) count += menu.getCarried().getCount();
        return count;
    }
    private static void requestOpen() {
        openRequested = System.nanoTime(); span = "nexus_open_" + opening;
        server(p -> {
            long start = System.nanoTime(); p.closeContainer(); NetworkManager.open(p, GlobalPos.of(p.level().dimension(), AT));
            report.put("serverOpen" + opening + "Nanos", System.nanoTime() - start);
        });
        phase = 2; age = 0;
    }
    private static void fill(int amount) throws Exception {
        if (ModList.get().isLoaded("emi")) Emi.fill(screen, selected.id(), amount);
        else {
            var field = com.cappleapple.astralrepository.compat.AstralJeiPlugin.class.getDeclaredField("runtime"); field.setAccessible(true);
            var runtime = (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
            var category = runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category).orElseThrow();
            check(handler.transferRecipe(screen.getMenu(), selected, null, Minecraft.getInstance().player, amount > 1, true) == null, "Actual JEI handler refused custom fill");
        }
    }
    private static final class Emi {
        static void tagEpoch(NexusMenu menu) {
            var temporary = new NexusScreen(menu, Minecraft.getInstance().player.getInventory(), Component.literal("Tag epoch fixture"));
            var handler = new com.cappleapple.astralrepository.compat.AstralEmiPlugin.RecipeHandler();
            var before = handler.getInventory(temporary);
            long epoch = RecipeTransfers.recipeEpoch();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.TagsUpdatedEvent(
                    Minecraft.getInstance().level.registryAccess(), true, true));
            check(RecipeTransfers.recipeEpoch() > epoch, "Integrated client tag packet did not reach cache hook");
            var after = handler.getInventory(temporary);
            check(before != after && !after.isEqual(before), "Tag-only epoch did not invalidate equal-stock EMI availability");
        }
        static void open(ResourceLocation id) { dev.emi.emi.api.EmiApi.displayRecipe(Objects.requireNonNull(dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(id))); }
        static void fill(NexusScreen screen, ResourceLocation id, int amount) throws Exception {
            var recipe = Objects.requireNonNull(dev.emi.emi.api.EmiApi.getRecipeManager().getRecipe(id));
            var type = dev.emi.emi.api.recipe.handler.EmiCraftContext.Type.FILL_BUTTON;
            var destination = dev.emi.emi.api.recipe.handler.EmiCraftContext.Destination.NONE;
            var method = Class.forName("dev.emi.emi.registry.EmiRecipeFiller").getMethod("performFill", dev.emi.emi.api.recipe.EmiRecipe.class,
                    net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, type.getClass(), destination.getClass(), int.class);
            check((boolean) method.invoke(null, recipe, screen, type, destination, amount), "Actual EMI fill refused custom recipe");
        }
    }
    private static Map<String,Object> frameReport() {
        Map<String,Object> result = new LinkedHashMap<>();
        frames.forEach((key, values) -> result.put(key, Map.of("samples", values.size(), "medianNanos", median(values), "maxNanos", Collections.max(values))));
        return result;
    }
    private static long median(List<Long> values) { return values.stream().sorted().toList().get(values.size() / 2); }
    private static void next(int value, String label) { phase = value; age = 0; span = label; }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); ready = false;
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())); ready = true; }
            catch (Throwable error) { failure = error; }
        });
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
