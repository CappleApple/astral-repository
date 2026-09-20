package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.compat.PatchouliIntegration;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.FieldGuideItem;
import com.cappleapple.astralrepository.content.RecipeTomeItem;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class GuidebookGameTests {
    private static final ResourceLocation GUIDE = PatchouliIntegration.BOOK;

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void fieldGuideRegistrationCreativeEntryAndUseFollowOptionalPatchouli(GameTestHelper h) {
        boolean installed = PatchouliIntegration.isLoaded();
        h.assertTrue(!Boolean.getBoolean("astral_repository.patchouliTest") || installed, "The requested Patchouli test profile must load its real implementation");
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(GUIDE) == installed, "The mod-owned guide item is registered only with Patchouli installed");
        h.assertTrue(AstralContent.FIELD_GUIDE.isPresent() == installed, "Optional registration matches the loaded mod set");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(GUIDE).isPresent() == installed, "The conditional recipe follows the optional item registration");
        var tab = AstralContent.CREATIVE_TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS, true, h.getLevel().registryAccess()));
        long copies = tab.getDisplayItems().stream().filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(GUIDE)).count();
        h.assertTrue(copies == (installed ? 1 : 0), "The creative tab contains exactly one guide with Patchouli and none without it");
        if (installed) Installed.checkIdentityAndItemUse(h);
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void actualCraftingResolvesOptionalFieldGuideWithoutShadowingRecipeTome(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        CraftingInput guideInput = CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.BOOK), new ItemStack(AstralContent.ASTRAL_GEM.get()),
                new ItemStack(AstralContent.ASTRAL_GEM.get()), ItemStack.EMPTY));
        var guideRecipe = recipes.getRecipeFor(RecipeType.CRAFTING, guideInput, h.getLevel());
        h.assertTrue(guideRecipe.isPresent() == PatchouliIntegration.isLoaded(), "Book plus two gems crafts a guide only while Patchouli is installed");
        if (guideRecipe.isPresent()) {
            var recipe = guideRecipe.orElseThrow();
            h.assertTrue(recipe.id().equals(GUIDE), "Book plus two gems resolves to the registered field-guide recipe");
            ItemStack craftedGuide = recipe.value().assemble(guideInput, h.getLevel().registryAccess());
            assertGuide(h, craftedGuide);
            h.assertTrue(craftedGuide.getCount() == 1 && ItemStack.isSameItemSameComponents(craftedGuide, Installed.bookStack()), "Actual assembly returns the mod-owned item resolved by Patchouli's custom_book_item");
            h.assertTrue(ItemStack.isSameItemSameComponents(craftedGuide, recipe.value().getResultItem(h.getLevel().registryAccess())), "The recipe preview retains the same guide identity as assembly");
        }
        CraftingInput tomeInput = CraftingInput.of(2, 2, List.of(
                new ItemStack(AstralContent.ASTRAL_GEM.get()), ItemStack.EMPTY,
                new ItemStack(Items.BOOK), ItemStack.EMPTY));
        var tomeRecipe = recipes.getRecipeFor(RecipeType.CRAFTING, tomeInput, h.getLevel()).orElseThrow();
        h.assertTrue(tomeRecipe.id().equals(ResourceLocation.fromNamespaceAndPath("astral_repository", "recipe_tome")), "The original vertical single-gem recipe always resolves to the Recipe Tome");
        ItemStack craftedTome = tomeRecipe.value().assemble(tomeInput, h.getLevel().registryAccess());
        h.assertTrue(craftedTome.is(AstralContent.RECIPE_TOME.get()), "The optional guide never substitutes for Recipe Tome crafting");
        h.assertTrue(guideRecipe.isEmpty() || !guideRecipe.orElseThrow().value().matches(tomeInput, h.getLevel()), "The field guide cannot shadow Recipe Tome crafting");
        h.assertTrue(!tomeRecipe.value().matches(guideInput, h.getLevel()), "The Recipe Tome cannot shadow field-guide crafting");
        h.succeed();
    }

    private static void assertGuide(GameTestHelper h, ItemStack guide) {
        h.assertTrue(!guide.isEmpty() && BuiltInRegistries.ITEM.getKey(guide.getItem()).equals(GUIDE) && guide.getItem() instanceof FieldGuideItem, "The guide owns the astral_repository:field_guide item ID");
        h.assertTrue(!guide.is(AstralContent.RECIPE_TOME.get()), "The guide never substitutes the Recipe Tome item");
    }

    /** Keep optional API types out of the test class loaded by the base GameTest scanner. */
    private static final class Installed {
        private static ItemStack bookStack() {
            // Patchouli93's public getBookStack always builds its generic guide_book.
            // The loaded declaration resolves custom_book_item through Book.getBookItem instead.
            var book = vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.get(GUIDE);
            if (book == null) throw new AssertionError("Patchouli did not load the Field Guide declaration");
            if (!book.noBook) throw new AssertionError("The declaration must disable Patchouli's duplicate generic book");
            return book.getBookItem().copy();
        }

        private static void checkIdentityAndItemUse(GameTestHelper h) {
            var api = vazkii.patchouli.api.PatchouliAPI.get();
            h.assertTrue(!api.isStub(), "The optional Patchouli profile exposes its real public API");
            h.assertTrue(api.getSubtitle(GUIDE) != null, "Patchouli loaded the guide declaration");
            ItemStack guide = bookStack();
            assertGuide(h, guide);
            ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), guide.save(h.getLevel().registryAccess())).orElseThrow();
            assertGuide(h, restored);
            h.assertTrue(ItemStack.isSameItemSameComponents(guide, restored), "The guide preserves its own registry identity through saving and loading");
            h.assertTrue(RecipeTomeItem.product(guide, h.getLevel().registryAccess()).isEmpty(), "A field guide cannot expose a Recipe Tome crafting-library product");
            var component = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("patchouli", "book"));
            h.assertTrue(component != null && AstralContent.CREATIVE_TAB.get().getDisplayItems().stream().noneMatch(stack -> GUIDE.equals(stack.get(component))), "Patchouli does not add a duplicate generic guide_book to the creative tab");

            var player = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "field-guide-use"));
            var payloads = new java.util.ArrayList<net.minecraft.network.protocol.common.custom.CustomPacketPayload>();
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(player.getServer(), player.connection.getConnection(), player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                    if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom) payloads.add(custom.payload());
                }
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { send(packet); }
            };
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, guide.copy());
            var result = guide.getItem().use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(result.getResult().consumesAction() && player.getMainHandItem().getCount() == 1, "Using the dedicated guide opens it without consuming the item");
            h.assertTrue(payloads.stream().anyMatch(payload -> payload instanceof vazkii.patchouli.network.MessageOpenBookGui open && GUIDE.equals(open.book()) && open.entry() == null && open.page() == 0), "Actual item use sends Patchouli's open-book packet for this guide");
        }
    }
    private GuidebookGameTests() {}
}
