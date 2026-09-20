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
import net.minecraft.resources.ResourceLocation;
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
        var recipe = page.entries().stream().filter(entry -> entry.recipe().equals(ResourceLocation.withDefaultNamespace("iron_trapdoor"))).findFirst().orElseThrow();
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
        h.assertTrue(!menu.inscribe(ResourceLocation.fromNamespaceAndPath("astral_repository", "missing_recipe")), "Unknown recipe identifiers are rejected");
        h.assertTrue(RecipeTomeItem.product(tome, h.getLevel().registryAccess()).isEmpty(), "Invalid requests do not invent a product");
        menu.action(new RecipeTomePackets.Action(81, 1, "", 0, "minecraft:iron_trapdoor"));
        h.assertTrue(RecipeTomeItem.product(tome, h.getLevel().registryAccess()).isEmpty(), "Actions for another menu cannot teach the book");
        ItemStack replacement = tome.copy(); player.setItemInHand(InteractionHand.OFF_HAND, replacement);
        h.assertTrue(!menu.stillValid(player), "Replacing the held book invalidates its original editing session");
        h.assertTrue(!menu.inscribe(ResourceLocation.withDefaultNamespace("iron_trapdoor")), "A stale session cannot edit the replacement");
        h.assertTrue(RecipeTomeItem.product(replacement, h.getLevel().registryAccess()).isEmpty(), "The replacement book stays unchanged");
        h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(h.getLevel(), profile).connection;
        return player;
    }
}
