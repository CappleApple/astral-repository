package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.crafting.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** The optional gate uses the installed Iron Furnaces block and its normal server ticker. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class IronFurnacesGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void ironFurnaceConsumesFuelAndReturnsActualSmeltedOutput(GameTestHelper h) {
        runCooking(h, "", Items.RAW_IRON, Items.IRON_INGOT);
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void ironFurnaceBlastingAugmentUsesBlastingRecipes(GameTestHelper h) {
        runCooking(h, "augment_blasting", Items.RAW_IRON, Items.IRON_INGOT);
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void ironFurnaceSmokingAugmentUsesSmokingRecipes(GameTestHelper h) {
        runCooking(h, "augment_smoking", Items.BEEF, Items.COOKED_BEEF);
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void ironFurnaceCancellationReturnsOnlyOwnedInputAndFuel(GameTestHelper h) {
        if (!requireIronFurnaces(h)) return;
        var fixture = fixture(h);
        fixture.chest.setItem(0, new ItemStack(Items.COAL));
        var adapter = new VanillaProcessingAdapter();
        var node = smeltingNode(adapter, fixture);
        var operation = adapter.start(fixture, fixture.at(fixture.processors.getFirst()), node);
        h.assertTrue(operation != null, "Installed Iron Furnace accepts a smelting operation");
        var returned = operation.cancel();
        h.assertTrue(returned.equals(Map.of(key(Items.RAW_IRON), 1L, key(Items.COAL), 1L)), "Cancellation conserves physical input and fuel in transit: " + returned);
        h.assertTrue(operation.cancel().isEmpty(), "Repeated cancellation cannot refund twice");
        Container furnace = (Container)h.getBlockEntity(fixture.processors.getFirst());
        h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(1).isEmpty() && furnace.getItem(2).isEmpty(), "No owned input or fuel remains in the machine");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void ironFactoryAndGeneratorAreNotTreatedAsThreeSlotFurnaces(GameTestHelper h) {
        if (!requireIronFurnaces(h)) return;
        var fixture = fixture(h);
        var position = fixture.at(fixture.processors.getFirst());
        var adapter = new VanillaProcessingAdapter();
        var node = smeltingNode(adapter, fixture);
        Container furnace = (Container)h.getBlockEntity(fixture.processors.getFirst());
        for (String augment : List.of("augment_factory", "augment_generator")) {
            furnace.setItem(5, item(augment));
            h.assertTrue(!adapter.supports(fixture, position, node) && adapter.start(fixture, position, node) == null, "Unsupported " + augment + " cannot receive a three-slot operation");
        }
        furnace.setItem(5, ItemStack.EMPTY);
        furnace.setItem(3, item("augment_blasting"));
        h.assertTrue(!adapter.supports(fixture, position, node), "A just-changed augment cannot accept the cached smelting mode");
        h.assertTrue(new CraftingService(fixture).isProcessingPosition(position), "Idle Iron Furnaces are excluded from ordinary output storage");
        h.succeed();
    }

    private static void runCooking(GameTestHelper h, String augment, net.minecraft.world.item.Item input, net.minecraft.world.item.Item output) {
        if (!requireIronFurnaces(h)) return;
        var fixture = fixture(h);
        var processor = fixture.processors.getFirst();
        if (!augment.isEmpty()) ((Container)h.getBlockEntity(processor)).setItem(3, item(augment));
        fixture.chest.setItem(0, new ItemStack(input)); fixture.chest.setItem(1, new ItemStack(Items.COAL));
        var access = new CraftingGameTests.Fixture(h, fixture.origin, fixture.chest, fixture.processors, new ItemStack(output));
        var service = new CraftingService(access);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "iron-furnace-test"), ClientInformation.createDefault());
        boolean[] requested = {false};
        // Let the real machine update its augment recipe type before catalog discovery.
        h.runAfterDelay(2, () -> {
            h.assertTrue(service.request(player, new ItemStack(output), 1).accepted(), "Iron Furnace request accepted");
            requested[0] = true;
        });
        h.onEachTick(() -> { if (requested[0]) service.tick(); });
        h.succeedWhen(() -> {
            h.assertTrue(requested[0] && service.activeJobs() == 0, "Actual Iron Furnace operation finished: " + service.statuses());
            h.assertTrue(access.snapshot().getOrDefault(key(output), 0L) == 1, "Actual output returned to storage");
            h.assertTrue(!access.snapshot().containsKey(key(input)) && !access.snapshot().containsKey(key(Items.COAL)), "The real machine consumed the ingredient and fuel");
            h.assertTrue(!CraftingService.isProcessorReserved(access.at(processor)), "Machine claim released");
        });
    }

    private static CraftingGameTests.Fixture fixture(GameTestHelper h) {
        var chest = new BlockPos(1, 2, 1); var furnace = new BlockPos(3, 2, 1);
        h.setBlock(chest, Blocks.CHEST);
        var id = ResourceLocation.parse("ironfurnaces:iron_furnace");
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Installed Iron Furnace block exists");
        h.setBlock(furnace, BuiltInRegistries.BLOCK.get(id));
        return new CraftingGameTests.Fixture(h, chest, (Container)h.getBlockEntity(chest), List.of(furnace), new ItemStack(Items.IRON_INGOT));
    }
    private static CraftPlan.Node<ItemKey> smeltingNode(VanillaProcessingAdapter adapter, CraftingGameTests.Fixture fixture) {
        var recipe = adapter.recipes(fixture).stream().filter(r -> r.id().equals("minecraft:iron_ingot_from_smelting_raw_iron")).findFirst().orElseThrow();
        return new CraftPlan.Node<>(0, recipe, List.of(new CraftPlan.Selection<>(key(Items.RAW_IRON), 1)), Set.of());
    }
    private static ItemKey key(net.minecraft.world.item.Item item) { return new ItemKey(new ItemStack(item)); }
    private static ItemStack item(String path) {
        var id = ResourceLocation.parse("ironfurnaces:" + path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Missing Iron Furnaces fixture item " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
    private static boolean requireIronFurnaces(GameTestHelper h) {
        if (ModList.get().isLoaded("ironfurnaces")) return true;
        h.assertTrue(!Boolean.getBoolean("astral_repository.recipeCompatTest"), "Iron Furnaces must be installed during the recipe compatibility gate");
        h.succeed(); return false;
    }
}
