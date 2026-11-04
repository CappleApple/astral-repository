package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import com.cappleapple.astralrepository.network.RecipeTomePackets;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class RecipeTomeGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void rightClickOpensAndTeachesActualRecipeIntoBookshelf(GameTestHelper h) {
        ServerPlayer player = player(h, "tome-editor");
        ItemStack tome = new ItemStack(AstralContent.RECIPE_TOME.get());
        CompoundTag custom = new CompoundTag(); custom.putString("PackNote", "retained"); tome.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        player.setItemInHand(InteractionHand.MAIN_HAND, tome);
        var result = tome.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult().consumesAction() && player.containerMenu instanceof RecipeTomeMenu, "Right-click opens the real tome menu");
        RecipeTomeMenu menu = (RecipeTomeMenu) player.containerMenu;
        var page = menu.browse("minecraft:iron_trapdoor", 0);
        var recipe = page.entries().stream().filter(entry -> entry.recipe().equals(Identifier.withDefaultNamespace("iron_trapdoor"))).findFirst().orElseThrow();
        h.assertTrue(recipe.output().is(Items.IRON_TRAPDOOR), "The catalogue uses the actual server result");
        h.assertTrue(recipe.ingredients().stream().filter(stack -> stack.is(Items.IRON_INGOT)).count() == 4, "The book previews actual crafting ingredients");
        menu.action(new RecipeTomePackets.Action(menu.containerId, 1, "", 0, recipe.recipe().toString()));
        h.assertTrue(RecipeTomeItem.product(tome, h.getLevel().registryAccess()).is(Items.IRON_TRAPDOOR), "Selecting a recipe saves its final product");
        h.assertTrue(tome.get(DataComponents.CUSTOM_DATA).copyTag().getString("PackNote").equals("retained"), "Unrelated tome data survives editing");
        h.assertTrue(tome.is(ItemTags.BOOKSHELF_BOOKS), "Tomes remain accepted bookshelf books");
        ItemStack restored = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) tome.save(h.getLevel().registryAccess()));
        BlockPos shelfPos = new BlockPos(3, 2, 3);
        h.setBlock(shelfPos, Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        ChiseledBookShelfBlockEntity shelf = (ChiseledBookShelfBlockEntity) h.getBlockEntity(shelfPos);
        player.closeContainer(); player.setItemInHand(InteractionHand.MAIN_HAND, restored);
        BlockPos absolute = h.absolutePos(shelfPos);
        BlockHitResult hit = new BlockHitResult(absolute.getCenter().add(-.3, .25, .5), Direction.SOUTH, absolute, false);
        shelf.getBlockState().useItemOn(restored, h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(RecipeTomeItem.product(shelf.getItem(0), h.getLevel().registryAccess()).is(Items.IRON_TRAPDOOR), "Vanilla bookshelf insertion keeps the taught output");
        shelf.getBlockState().useWithoutItem(h.getLevel(), player, hit);
        ItemStack retrieved = player.getInventory().items.stream().filter(stack -> stack.is(AstralContent.RECIPE_TOME.get())).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(shelf.getItem(0).isEmpty() && RecipeTomeItem.product(retrieved, h.getLevel().registryAccess()).is(Items.IRON_TRAPDOOR), "Vanilla bookshelf retrieval retains the saved craft-library product");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void invalidRecipeAndReplacedHeldTomeCannotBeInscribed(GameTestHelper h) {
        ServerPlayer player = player(h, "tome-validation");
        ItemStack tome = new ItemStack(AstralContent.RECIPE_TOME.get()); player.setItemInHand(InteractionHand.OFF_HAND, tome);
        RecipeTomeMenu menu = new RecipeTomeMenu(80, player.getInventory(), InteractionHand.OFF_HAND);
        h.assertTrue(menu.stillValid(player), "Offhand tome sessions are supported");
        h.assertTrue(!menu.inscribe(Identifier.fromNamespaceAndPath("astral_repository", "missing_recipe")), "Unknown recipe identifiers are rejected");
        h.assertTrue(RecipeTomeItem.product(tome, h.getLevel().registryAccess()).isEmpty(), "Invalid requests do not invent a product");
        menu.action(new RecipeTomePackets.Action(81, 1, "", 0, "minecraft:iron_trapdoor"));
        h.assertTrue(RecipeTomeItem.product(tome, h.getLevel().registryAccess()).isEmpty(), "Actions for another menu cannot teach the book");
        ItemStack replacement = tome.copy(); player.setItemInHand(InteractionHand.OFF_HAND, replacement);
        h.assertTrue(!menu.stillValid(player), "Replacing the held book invalidates its original editing session");
        h.assertTrue(!menu.inscribe(Identifier.withDefaultNamespace("iron_trapdoor")), "A stale session cannot edit the replacement");
        h.assertTrue(RecipeTomeItem.product(replacement, h.getLevel().registryAccess()).isEmpty(), "The replacement book stays unchanged");
        h.succeed();
    }
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void pathExclusionsPersistAndSyncWithoutChangingTheTaughtOutput(GameTestHelper h) {
        ServerPlayer player = player(h, "tome-exclusions");
        ItemStack tome = new ItemStack(AstralContent.RECIPE_TOME.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tome);
        RecipeTomeMenu menu = new RecipeTomeMenu(82, player.getInventory());
        Identifier target = Identifier.withDefaultNamespace("iron_trapdoor");
        h.assertTrue(!menu.exclude(target, true), "An empty tome must be inscribed before paths can be edited");
        h.assertTrue(menu.inscribe(target), "The final target is inscribed first");
        RecipeTomeMenu.Entry unpack = null;
        var first = menu.browse("=minecraft:iron_ingot", 0);
        for (int page = 0; page < first.total() && unpack == null; page++) {
            unpack = menu.browse("=minecraft:iron_ingot", page).entries().stream()
                    .filter(entry -> entry.ingredients().stream().anyMatch(stack -> stack.is(Items.IRON_BLOCK)))
                    .findFirst().orElse(null);
        }
        h.assertTrue(unpack != null, "The actual server recipe converts an iron block to ingots");
        Identifier blocked = unpack.recipe();
        menu.action(new RecipeTomePackets.Action(83, 3, "", 0, blocked.toString()));
        h.assertTrue(RecipeTomeItem.excludedRecipes(tome).isEmpty(), "A different menu cannot alter path policy");
        menu.action(new RecipeTomePackets.Action(82, 3, "", 0, blocked.toString()));
        h.assertTrue(RecipeTomeItem.excludedRecipes(tome).contains(blocked.toString()), "The block action records the exact recipe ID");
        h.assertTrue(RecipeTomeItem.product(tome, h.getLevel().registryAccess()).is(Items.IRON_TRAPDOOR), "Browsing and excluding an intermediate recipe preserves the final target");
        h.assertTrue(menu.inscribe(target) && RecipeTomeItem.excludedRecipes(tome).contains(blocked.toString()), "Reinscribing the same product preserves its policies");
        var saved = com.cappleapple.astralrepository.port.NbtCodecs.item(h.getLevel().registryAccess(), com.cappleapple.astralrepository.port.NbtCodecs.save(tome,h.getLevel().registryAccess()));
        h.assertTrue(RecipeTomeItem.excludedRecipes(saved).contains(blocked.toString()), "Path policy survives stack serialization");
        var page = new RecipeTomePackets.Page(82, 0, 1, RecipeTomeItem.product(tome, h.getLevel().registryAccess()), "", java.util.List.of(
                new RecipeTomeMenu.Entry(unpack.recipe(), unpack.type(), unpack.output(), unpack.ingredients(), true)));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            RecipeTomePackets.Page.CODEC.encode(buffer, page);
            var decoded = RecipeTomePackets.Page.CODEC.decode(buffer);
            h.assertTrue(decoded.entries().getFirst().excluded() && decoded.entries().getFirst().recipe().equals(blocked), "The menu packet retains the exact recipe and excluded state");
        } finally { buffer.release(); }
        h.assertTrue(menu.exclude(blocked, false) && RecipeTomeItem.excludedRecipes(tome).isEmpty(), "Allowing the recipe removes the exclusion");
        h.assertTrue(!menu.exclude(Identifier.fromNamespaceAndPath("astral_repository", "missing"), true), "Unknown recipe IDs cannot be blocked");
        menu.exclude(blocked, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, tome.copy());
        h.assertTrue(!menu.exclude(blocked, false) && RecipeTomeItem.excludedRecipes(tome).contains(blocked.toString()), "A replaced held tome invalidates exclusion edits");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void waitingCraftRetriesWhenItsTomeAllowsTheRecipe(GameTestHelper h) {
        BlockPos chestPos = new BlockPos(3, 2, 3), table = chestPos.east();
        h.setBlock(chestPos, Blocks.BARREL); h.setBlock(table, Blocks.CRAFTING_TABLE);
        var chest = (net.minecraft.world.Container) h.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        java.util.Set<String> excluded = new java.util.HashSet<>(java.util.Set.of("minecraft:iron_trapdoor"));
        var access = new CraftingGameTests.Fixture(h, chestPos, chest, java.util.List.of(table), new ItemStack(Items.IRON_TRAPDOOR)) {
            @Override public java.util.Set<String> excludedRecipes(ItemStack target) { return java.util.Set.copyOf(excluded); }
        };
        var service = new com.cappleapple.astralrepository.crafting.CraftingService(access);
        var request = service.request(player(h, "tome-policy-retry"), new ItemStack(Items.IRON_TRAPDOOR), 1);
        h.assertTrue(request.accepted(), "The taught target can be requested while all paths are blocked");
        CraftingGameTests.awaitPlanning(service);
        h.assertTrue(chest.getItem(0).getCount() == 4 && service.statuses().stream().noneMatch(job -> job.state().equals("RUNNING")), "Blocked paths do not reserve ingredients");
        excluded.clear(); CraftingGameTests.awaitPlanning(service);
        for (int tick = 0; tick < 80; tick++) service.tick();
        h.assertTrue(service.activeJobs() == 0 && access.snapshot().getOrDefault(new com.cappleapple.astralrepository.api.ItemKey(new ItemStack(Items.IRON_TRAPDOOR)), 0L) == 1,
                "Allowing a recipe invalidates the waiting plan and produces the requested output");
        chest.clearContent(); chest.setItem(0, new ItemStack(Items.IRON_INGOT, 4)); excluded.add("minecraft:iron_trapdoor");
        h.assertTrue(service.request(player(h, "tome-policy-cache"), new ItemStack(Items.IRON_TRAPDOOR), 1).accepted(), "A second request is queued after policy changes");
        CraftingGameTests.awaitPlanning(service);
        h.assertTrue(chest.getItem(0).getCount() == 4 && service.activeProcessors() == 0, "A cached allowed plan cannot bypass a newly blocked recipe");
        service.cancelAll();
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=600)
    public static void connectedShelfPoliciesAreScopedToTheTaughtOutputAndRemovedWithTheShelf(GameTestHelper h) {
        BlockPos nexusPos = new BlockPos(5, 2, 5), shelfPos = nexusPos.north();
        h.setBlock(nexusPos, AstralContent.STORAGE_NEXUS.get()); h.setBlock(shelfPos, Blocks.CHISELED_BOOKSHELF);
        var player = player(h, "tome-shelf-policy");
        var tome = new ItemStack(AstralContent.RECIPE_TOME.get()); player.setItemInHand(InteractionHand.MAIN_HAND, tome);
        var editor = new RecipeTomeMenu(84, player.getInventory());
        h.assertTrue(editor.inscribe(Identifier.withDefaultNamespace("iron_trapdoor")), "The connected library target is inscribed");
        h.assertTrue(editor.exclude(Identifier.withDefaultNamespace("iron_block"), true), "An intermediate recipe is excluded");
        ((net.minecraft.world.Container) h.getBlockEntity(shelfPos)).setItem(0, tome.copy());
        var manager = com.cappleapple.astralrepository.network.NetworkManager.get(h.getLevel().getServer());
        var address = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(nexusPos));
        h.startSequence().thenWaitUntil(() -> {
            var network = manager.networkAt(address);
            h.assertTrue(network != null && network.exposedProducts().stream().anyMatch(stack -> stack.is(Items.IRON_TRAPDOOR)), "The real shelf is discovered");
            h.assertTrue(network.excludedRecipes(new ItemStack(Items.IRON_TRAPDOOR)).contains("minecraft:iron_block"), "The network reads the tome's recipe policy");
            h.assertTrue(network.excludedRecipes(new ItemStack(Items.IRON_INGOT)).isEmpty(), "Other final products retain their own recipe paths");
        }).thenExecute(() -> {
            h.setBlock(shelfPos, Blocks.AIR);
            com.cappleapple.astralrepository.network.NetworkManager.changed(h.getLevel(), h.absolutePos(shelfPos));
        }).thenWaitUntil(() -> h.assertTrue(manager.networkAt(address).excludedRecipes(new ItemStack(Items.IRON_TRAPDOOR)).isEmpty(), "Removing the shelf removes its recipe policy"))
                .thenExecute(h::succeed);
    }

    private static ServerPlayer player(GameTestHelper h, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(h.getLevel(), profile).connection;
        return player;
    }
}
