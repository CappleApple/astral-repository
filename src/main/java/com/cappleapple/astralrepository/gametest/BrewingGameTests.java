package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import com.cappleapple.astralrepository.crafting.*;
import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import com.cappleapple.astralrepository.network.RecipeTomePackets;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class BrewingGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void potionSelectionPacketAndTomeKeepTheExactBrewedVariant(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "potion-tome"));
        var tome = new ItemStack(AstralContent.RECIPE_TOME.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tome);
        var menu = new RecipeTomeMenu(91, player.getInventory());
        var chosen = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.LONG_SWIFTNESS);
        var action = new RecipeTomePackets.Action(91, 2, "minecraft:splash_potion", 0, "", chosen);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            RecipeTomePackets.Action.CODEC.encode(buffer, action);
            var decoded = RecipeTomePackets.Action.CODEC.decode(buffer);
            h.assertTrue(ItemStack.isSameItemSameComponents(decoded.selection(), chosen), "The selection packet preserves potion contents");
            menu.action(decoded);
        } finally { buffer.release(); }
        var page = menu.browse("=minecraft:splash_potion", 0);
        h.assertTrue(page.total() > 0 && page.entries().stream().allMatch(entry -> ItemStack.isSameItemSameComponents(entry.output(), chosen)),
                "The server resolves the selected potion variant instead of an uncraftable base potion");
        var recipe = page.entries().getFirst();
        h.assertTrue(recipe.type().equals(ResourceLocation.withDefaultNamespace("brewing")), "The tome identifies the brewing process");
        h.assertTrue(menu.inscribe(recipe.recipe()), "A concrete server brewing rule can be inscribed");
        h.assertTrue(ItemStack.isSameItemSameComponents(RecipeTomeItem.product(tome, h.getLevel().registryAccess()), chosen), "Inscription retains duration and splash container");
        ItemStack restored = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) tome.save(h.getLevel().registryAccess()));
        h.assertTrue(ItemStack.isSameItemSameComponents(RecipeTomeItem.product(restored, h.getLevel().registryAccess()), chosen), "Tome serialization preserves potion identity");
        h.assertTrue(menu.exclude(recipe.recipe(), true) && RecipeTomeItem.excludedRecipes(tome).contains(recipe.recipe().toString()), "Brewing paths support the same recipe exclusion policy");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=1200)
    public static void realBrewingStandExecutesIntermediatePotionsAndConsumesRealFuel(GameTestHelper h) {
        var chestPos = new BlockPos(3, 2, 3); var standPos = chestPos.east();
        h.setBlock(chestPos, Blocks.BARREL); h.setBlock(standPos, Blocks.BREWING_STAND);
        var chest = (Container) h.getBlockEntity(chestPos);
        chest.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        chest.setItem(1, new ItemStack(Items.NETHER_WART)); chest.setItem(2, new ItemStack(Items.SUGAR));
        chest.setItem(3, new ItemStack(Items.BLAZE_POWDER));
        var target = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
        var access = new CraftingGameTests.Fixture(h, chestPos, chest, List.of(standPos), target);
        var service = new CraftingService(access);
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "brew-chain"));
        h.assertTrue(service.request(player, target, 1).accepted(), "The taught potion request is accepted");
        CraftingGameTests.awaitPlanning(service);
        boolean[] awkward = {false};
        var intermediate = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        h.onEachTick(() -> {
            service.tick();
            awkward[0] |= ItemStack.isSameItemSameComponents(((Container) h.getBlockEntity(standPos)).getItem(0), intermediate);
        });
        h.succeedWhen(() -> {
            h.assertTrue(service.activeJobs() == 0, "Both physical brewing operations complete");
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(target), 0L) == 1, "The exact potion returns to storage");
            h.assertTrue(awkward[0], "An actual awkward potion existed in the real stand before the second brew");
            h.assertTrue(access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.NETHER_WART)), 0L) == 0
                    && access.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.SUGAR)), 0L) == 0, "Both reagents were consumed once");
            h.assertTrue(chest.getItem(3).isEmpty() && ((Container) h.getBlockEntity(standPos)).getItem(4).isEmpty(), "The stand consumed actual blaze powder");
            h.assertTrue(!CraftingService.isProcessorReserved(access.at(standPos)), "The brewing stand claim is released");
        });
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void cancellingBrewingReturnsArrivedInputsAndFuelInFlightOnce(GameTestHelper h) {
        var chestPos = new BlockPos(3, 2, 3); var standPos = chestPos.east(3);
        h.setBlock(chestPos, Blocks.BARREL); h.setBlock(standPos, Blocks.BREWING_STAND);
        var chest = (Container) h.getBlockEntity(chestPos); chest.setItem(0, new ItemStack(Items.BLAZE_POWDER));
        var target = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        var access = new CraftingGameTests.Fixture(h, chestPos, chest, List.of(standPos), target);
        var adapter = new BrewingProcessingAdapter();
        var recipe = adapter.recipes(access).stream().filter(entry -> entry.output().equals(new ItemKey(target))).findFirst().orElseThrow();
        var selections = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().getFirst(), input.count())).toList();
        var node = new CraftPlan.Node<>(0, recipe, selections, Set.of());
        var operation = adapter.start(access, access.at(standPos), node);
        h.assertTrue(operation != null && operation.recoverable().getOrDefault(new ItemKey(new ItemStack(Items.BLAZE_POWDER)), 0L) == 1,
                "Fuel stays in recoverable transit escrow until arrival");
        var refund = operation.cancel();
        h.assertTrue(refund.getOrDefault(new ItemKey(new ItemStack(Items.BLAZE_POWDER)), 0L) == 1
                && refund.getOrDefault(new ItemKey(new ItemStack(Items.NETHER_WART)), 0L) == 1
                && refund.getOrDefault(new ItemKey(PotionContents.createItemStack(Items.POTION, Potions.WATER)), 0L) == 1,
                "Cancellation returns only the real bottle, reagent and pending fuel");
        h.assertTrue(operation.cancel().isEmpty() && ((Container) h.getBlockEntity(standPos)).getItem(0).isEmpty(), "Repeated cancellation cannot duplicate the refund");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void cancellingAfterPhysicalBrewingKeepsThePotionAndReagentBottleRemainder(GameTestHelper h) {
        var chestPos = new BlockPos(3, 2, 3); var standPos = chestPos.east();
        h.setBlock(chestPos, Blocks.BARREL); h.setBlock(standPos, Blocks.BREWING_STAND);
        var chest = (Container) h.getBlockEntity(chestPos);
        var target = PotionContents.createItemStack(Items.LINGERING_POTION, Potions.WATER);
        var access = new CraftingGameTests.Fixture(h, chestPos, chest, List.of(standPos), target);
        var stand = (net.minecraft.world.level.block.entity.BrewingStandBlockEntity) h.getBlockEntity(standPos);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(h.getLevel(), stand.getBlockPos(), stand.getBlockState(), stand);
        var adapter = new BrewingProcessingAdapter();
        var recipe = adapter.recipes(access).stream().filter(entry -> entry.output().equals(new ItemKey(target))).findFirst().orElseThrow();
        var selections = recipe.ingredients().stream().map(input -> new CraftPlan.Selection<>(input.alternatives().getFirst(), input.count())).toList();
        var operation = adapter.start(access, access.at(standPos), new CraftPlan.Node<>(0, recipe, selections, Set.of()));
        h.assertTrue(operation != null, "A charged stand can brew without taking another blaze powder");
        for (int tick = 0; tick < 402; tick++)
            net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(h.getLevel(), stand.getBlockPos(), stand.getBlockState(), stand);
        h.assertTrue(ItemStack.isSameItemSameComponents(stand.getItem(0), target) && stand.getItem(3).is(Items.GLASS_BOTTLE), "The real dragon breath brew leaves its glass bottle");
        var refund = operation.cancel();
        h.assertTrue(refund.getOrDefault(new ItemKey(target), 0L) == 1
                && refund.getOrDefault(new ItemKey(new ItemStack(Items.GLASS_BOTTLE)), 0L) == 1
                && !refund.containsKey(new ItemKey(new ItemStack(Items.DRAGON_BREATH))), "Cancellation collects the finished potion and its real remainder without refunding consumed reagent");
        h.assertTrue(operation.cancel().isEmpty() && stand.getItem(0).isEmpty() && stand.getItem(3).isEmpty(), "All collected physical contents are removed exactly once");
        h.succeed();
    }
}
