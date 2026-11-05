package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NexusAutocraftingGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void autocraftingWaitsForPhysicalTableAndPreservesPersonalGrid(GameTestHelper h) {
        var nexus = new BlockPos(5, 2, 5);
        var chest = nexus.west();
        var table = nexus.north();
        h.setBlock(nexus, AstralContent.STORAGE_NEXUS.get());
        h.setBlock(chest, Blocks.CHEST);
        var storage = (Container) h.getBlockEntity(chest);
        storage.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        var at = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(nexus));
        var manager = NetworkManager.get(h.getLevel().getServer());
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "nexus-autocraft"));
        var tome = new ItemStack(AstralContent.RECIPE_TOME.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tome);
        var editor = new RecipeTomeMenu(500, player.getInventory());
        h.assertTrue(editor.inscribe(ResourceLocation.withDefaultNamespace("iron_trapdoor")), "Teach the actual recipe");
        var shelf = nexus.east();
        h.setBlock(shelf, Blocks.CHISELED_BOOKSHELF);
        ((Container) h.getBlockEntity(shelf)).setItem(0, tome);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var menu = new NexusMenu(501, player.getInventory());
        player.containerMenu = menu;
        menu.grid.setItem(4, new ItemStack(Items.DIAMOND, 3));
        var target = new ItemStack(Items.IRON_TRAPDOOR);
        h.startSequence().thenWaitUntil(() -> {
            var network = manager.networkAt(at);
            h.assertTrue(network != null
                    && network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 4
                    && network.exposedProducts().stream().anyMatch(stack -> stack.is(Items.IRON_TRAPDOOR)),
                    "The actual network discovers the Nexus, tome, and stored ingredients");
            h.assertTrue(!com.cappleapple.astralrepository.crafting.VanillaProcessingAdapter.isCraftingWorkstation(h.getLevel(), at.pos()),
                    "The personal Nexus grid is not an autocrafting workstation");
        }).thenExecute(() -> {
            var network = manager.networkAt(at);
            h.assertTrue(network.crafting().request(player, target, 1).accepted(), "A taught request can wait for its physical crafting table");
        }).thenWaitUntil(() -> {
            var service = manager.networkAt(at).crafting();
            h.assertTrue(service.statuses().stream().anyMatch(job -> job.state().equals("WAITING") || job.state().equals("MISSING")),
                    "Planning reports unavailable processing instead of using the Nexus grid");
        }).thenExecute(() -> {
            var network = manager.networkAt(at);
            h.assertTrue(network.crafting().activeJobs() == 1 && network.crafting().activeProcessors() == 0
                    && network.snapshot().getOrDefault(new ItemKey(new ItemStack(Items.IRON_INGOT)), 0L) == 4
                    && !network.snapshot().containsKey(new ItemKey(target)),
                    "Without a table the job waits and leaves all four real ingredients in storage");
            h.assertTrue(menu.grid.getItem(4).is(Items.DIAMOND) && menu.grid.getItem(4).getCount() == 3,
                    "Waiting leaves the saved manual grid untouched");
            h.setBlock(table, Blocks.CRAFTING_TABLE);
        }).thenWaitUntil(() -> {
            var network = manager.networkAt(at);
            h.assertTrue(network.crafting().activeJobs() == 0 && network.snapshot().getOrDefault(new ItemKey(target), 0L) == 1,
                    "Placing a real table resumes the same queued request and returns its output");
            h.assertTrue(!network.snapshot().containsKey(new ItemKey(new ItemStack(Items.IRON_INGOT))),
                    "Ingredients are consumed exactly once");
            h.assertTrue(menu.grid.getItem(4).is(Items.DIAMOND) && menu.grid.getItem(4).getCount() == 3,
                    "Automatic crafting leaves the player's saved manual grid untouched");
            var reopened = new NexusMenu(502, player.getInventory());
            h.assertTrue(reopened.grid.getItem(4).is(Items.DIAMOND) && reopened.grid.getItem(4).getCount() == 3,
                    "Reopening the Nexus restores the same saved manual ingredients");
        }).thenSucceed();
    }

    private NexusAutocraftingGameTests() {}
}
