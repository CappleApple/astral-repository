package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.api.*;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.crafting.*;
import com.cappleapple.astralrepository.menu.RecipeTomeMenu;
import com.cappleapple.astralrepository.network.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** Native output aliases are server-owned knowledge, including outputs absent from RecipeManager. */
@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NativeRecipeKnowledgeGameTests {
    private static final Map<GlobalPos, CraftRecipe<ResourceKey>> FIXTURES = new HashMap<>();
    private static boolean registered;
    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=100)
    public static void nativeDisplaySamplesCanonicalizeDirectHoldersWithoutChangingTheirSource(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var potion = net.minecraft.world.item.alchemy.Potions.SWIFTNESS;
        var contents = new net.minecraft.world.item.alchemy.PotionContents(Optional.of(Holder.direct(potion.value())),
                Optional.of(0x224466), List.of(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LUCK, 123)));
        ItemStack source = new ItemStack(Items.POTION); source.set(DataComponents.POTION_CONTENTS, contents);
        var enchantment = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
        for (var component : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
            var display = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY.withTooltip(false));
            display.set(Holder.direct(enchantment.value()), component == DataComponents.ENCHANTMENTS ? 3 : 5);
            source.set(component, display.toImmutable());
        }
        ItemStack normalized = NativeRecipeSource.normalizeDisplay(source, registries);
        var normalizedContents = normalized.get(DataComponents.POTION_CONTENTS);
        h.assertTrue(normalizedContents.potion().orElseThrow().unwrapKey().equals(potion.unwrapKey())
                && normalizedContents.customColor().equals(contents.customColor()) && normalizedContents.customEffects().equals(contents.customEffects()), "Registered direct potion display becomes its exact registry reference while retaining custom data");
        h.assertTrue(normalized.get(DataComponents.ENCHANTMENTS).getLevel(enchantment) == 3
                && normalized.get(DataComponents.STORED_ENCHANTMENTS).getLevel(enchantment) == 5, "Both direct enchantment display components retain their actual levels");
        h.assertTrue(source.get(DataComponents.POTION_CONTENTS).potion().orElseThrow().unwrapKey().isEmpty()
                && source.get(DataComponents.ENCHANTMENTS).keySet().iterator().next().unwrapKey().isEmpty(), "Native metadata samples remain unchanged");
        var identity = new ItemKey(normalized);
        h.assertTrue(ResourceKeyCodec.load(ResourceKeyCodec.save(identity, registries), registries).equals(identity), "Canonical native potion and enchantment displays survive durable resource serialization");
        ItemStack unregistered = new ItemStack(Items.POTION);
        unregistered.set(DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(Optional.of(Holder.direct(new net.minecraft.world.item.alchemy.Potion(
                new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 217)))), Optional.of(0xabcdef), contents.customEffects()));
        ItemStack combined = NativeRecipeSource.normalizeDisplay(unregistered, registries);
        var combinedContents = combined.get(DataComponents.POTION_CONTENTS);
        h.assertTrue(combinedContents.potion().isEmpty() && combinedContents.customEffects().size() == 2
                && combinedContents.customColor().equals(Optional.of(0xabcdef)), "Truly unregistered display potions retain their combined actual effects and color");
        var combinedIdentity = new ItemKey(combined);
        h.assertTrue(ResourceKeyCodec.load(ResourceKeyCodec.save(combinedIdentity, registries), registries).equals(combinedIdentity), "Unregistered native display potion effects are serializable without an invented registry ID");
        h.succeed();
    }
    private static synchronized void register() {
        if (registered) return;
        ProcessingAdapters.registerResource("astral_repository:native_tome_fixture", () -> new ResourceProcessingAdapter() {
            public List<CraftRecipe<ResourceKey>> recipes(CraftingService.NetworkAccess access) {
                var recipe = FIXTURES.get(access.origin());
                if (recipe == null) return List.of();
                var unbound = new CraftRecipe<>(recipe.id().replace(":native/test/profile/", ":mekanism/test/unbound/"),
                        recipe.output(), 999, "mekanism/mekanism:fixture/unbound", recipe.ingredients(), 0, 1);
                return List.of(recipe, unbound);
            }
            public boolean supports(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node) { return false; }
            public CraftScheduler.Operation<ResourceKey> start(CraftingService.NetworkAccess access, GlobalPos position, CraftPlan.Node<ResourceKey> node) { throw new AssertionError("Reading native recipe knowledge must never start a craft"); }
            public Object catalogState(CraftingService.NetworkAccess access) { return recipes(access); }
            public String workstationLabel(CraftingService.NetworkAccess access, String id) { return "Fixture native processor"; }
        });
        registered = true;
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=300)
    public static void nativeWorkstationPagesPreserveTheOrdinaryMinecraftCraftingAlternative(GameTestHelper h) {
        register(); BlockPos relative = new BlockPos(4, 2, 4); h.setBlock(relative, AstralContent.STORAGE_NEXUS.get());
        ((CrystalNodeBlockEntity)h.getBlockEntity(relative)).setChannel(14);
        GlobalPos origin = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(relative));
        var vanilla = h.getLevel().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .filter(holder -> holder.value().getResultItem(h.getLevel().registryAccess()).is(Items.CRAFTING_TABLE)).findFirst().orElseThrow();
        ItemStack output = vanilla.value().getResultItem(h.getLevel().registryAccess()).copy();
        FIXTURES.put(origin, new CraftRecipe<>("astral_repository:native/test/profile/" + vanilla.id(), new ItemKey(output), output.getCount(),
                "native/astral_repository/minecraft:crafting", List.of(CraftRecipe.Ingredient.of(new ItemKey(new ItemStack(Items.OAK_PLANKS)))), 0, 1));
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "native-tome-vanilla"));
        player.setPos(origin.pos().getX() + 1.5, origin.pos().getY() + 0.5, origin.pos().getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AstralContent.RECIPE_TOME.get()));
        RecipeTomeMenu menu = new RecipeTomeMenu(218, player.getInventory());
        var manager = NetworkManager.get(h.getLevel().getServer()); NetworkManager.changed(h.getLevel(), origin.pos());
        h.startSequence().thenWaitUntil(() -> h.assertTrue(manager.networkAt(origin) != null
                && NativeRecipeKnowledge.entries(player).stream().anyMatch(entry -> entry.recipe().equals(vanilla.id())), "The native alternative is visible on the nearby accessible network"))
                .thenExecute(() -> {
                    try {
                        menu.action(new RecipeTomePackets.Action(218, 2, "minecraft:crafting_table", 0, "", output));
                        var alternatives = pages(menu, "=minecraft:crafting_table").stream().filter(entry -> entry.recipe().equals(vanilla.id())).toList();
                        h.assertTrue(alternatives.stream().anyMatch(entry -> entry.process().isEmpty())
                                && alternatives.stream().anyMatch(entry -> entry.process().equals("Fixture native processor")), "The same Minecraft recipe retains both the ordinary crafting-table page and its actual native workstation page");
                        h.assertTrue(menu.inscribe(vanilla.id()) && ItemStack.isSameItemSameComponents(RecipeTomeItem.product(player.getMainHandItem(), player.registryAccess()), output), "The real Inscribe action teaches the canonical server output before recipe exclusions are edited");
                        h.assertTrue(menu.exclude(vanilla.id(), true), "A canonical recipe exclusion is accepted");
                        h.assertTrue(pages(menu, "=minecraft:crafting_table").stream().filter(entry -> entry.recipe().equals(vanilla.id())).allMatch(entry -> entry.excluded()), "Canonical exclusions apply consistently to both processing alternatives");
                        h.assertTrue(manager.networkAt(origin).crafting().activeJobs() == 0, "Browsing both alternatives starts no craft");
                    } finally { FIXTURES.remove(origin); }
                }).thenSucceed();
    }
    private static List<RecipeTomeMenu.Entry> pages(RecipeTomeMenu menu, String query) {
        var first = menu.browse(query, 0); var entries = new ArrayList<>(first.entries());
        for (int page = 1; page * RecipeTomeMenu.PAGE_SIZE < first.total(); page++) entries.addAll(menu.browse(query, page).entries());
        return List.copyOf(entries);
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=300)
    public static void nativeGrowthPagesShowGrossPhysicalOutputRatherThanPlannerNetCredit(GameTestHelper h) {
        register(); BlockPos relative = new BlockPos(4, 2, 4); h.setBlock(relative, AstralContent.STORAGE_NEXUS.get());
        ((CrystalNodeBlockEntity)h.getBlockEntity(relative)).setChannel(13);
        GlobalPos origin = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(relative));
        ItemKey seed = new ItemKey(new ItemStack(Items.WHEAT_SEEDS));
        ResourceLocation canonical = ResourceLocation.fromNamespaceAndPath("astral_repository", "implicit_native_growth");
        FIXTURES.put(origin, new CraftRecipe<>("astral_repository:native/test/profile/" + canonical, seed, 2,
                "native/astral_repository/growth", List.of(CraftRecipe.Ingredient.of(seed)), 0, 1, 1));
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "native-growth-page"));
        player.setPos(origin.pos().getX() + 1.5, origin.pos().getY() + 0.5, origin.pos().getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AstralContent.RECIPE_TOME.get()));
        RecipeTomeMenu menu = new RecipeTomeMenu(219, player.getInventory());
        var manager = NetworkManager.get(h.getLevel().getServer()); NetworkManager.changed(h.getLevel(), origin.pos());
        h.startSequence().thenWaitUntil(() -> h.assertTrue(NativeRecipeKnowledge.entries(player).stream().anyMatch(entry -> entry.recipe().equals(canonical)),
                "The native growth alias becomes visible without executing a craft"))
                .thenExecute(() -> {
                    try {
                        menu.action(new RecipeTomePackets.Action(219, 2, "minecraft:wheat_seeds", 0, "", seed.sample()));
                        var page = menu.browse("=minecraft:wheat_seeds", 0);
                        var entry = page.entries().stream().filter(value -> value.recipe().equals(canonical)).findFirst().orElseThrow();
                        h.assertTrue(entry.output().getCount() == 3 && entry.ingredients().getFirst().getCount() == 1,
                                "Tome shows one actual seed input and three actual outputs, while planner credit remains two");
                        h.assertTrue(menu.inscribe(canonical) && RecipeTomeItem.product(player.getMainHandItem(), player.registryAccess()).is(Items.WHEAT_SEEDS),
                                "Gross result metadata remains server-authoritative inscribable knowledge");
                        h.assertTrue(manager.networkAt(origin).crafting().activeJobs() == 0, "Reading growth metadata starts no craft");
                    } finally { FIXTURES.remove(origin); }
                }).thenSucceed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop", timeoutTicks=400)
    public static void nativeAliasInscriptionResolvesExactServerOutputAndEnforcesLocalNetworkAccess(GameTestHelper h) {
        register(); BlockPos relative = new BlockPos(4, 2, 4); h.setBlock(relative, AstralContent.STORAGE_NEXUS.get());
        ((CrystalNodeBlockEntity)h.getBlockEntity(relative)).setChannel(15);
        GlobalPos origin = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(relative));
        CompoundTag data = new CompoundTag(); data.putString("nativeRecipeVariant", "server-owned");
        ItemStack output = new ItemStack(Items.PAPER); output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        ResourceLocation canonical = ResourceLocation.fromNamespaceAndPath("astral_repository", "implicit_native_paper");
        String alias = "astral_repository:native/test/profile/" + canonical;
        FIXTURES.put(origin, new CraftRecipe<>(alias, new ItemKey(output), 1, "native/astral_repository/minecraft:smelting",
                List.of(CraftRecipe.Ingredient.of(new ItemKey(new ItemStack(Items.BRICK)))), 0, 1));
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "native-tome-access"));
        player.setPos(origin.pos().getX() + 1.5, origin.pos().getY() + 0.5, origin.pos().getZ() + 0.5);
        ItemStack tome = new ItemStack(AstralContent.RECIPE_TOME.get()); player.setItemInHand(InteractionHand.MAIN_HAND, tome);
        RecipeTomeMenu menu = new RecipeTomeMenu(217, player.getInventory());
        var manager = NetworkManager.get(h.getLevel().getServer()); NetworkManager.changed(h.getLevel(), origin.pos());
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(manager.networkAt(origin) != null && NativeRecipeKnowledge.entries(player).stream().anyMatch(entry -> entry.recipe().equals(canonical)), "Loaded local native catalog becomes discoverable without starting a job");
        }).thenExecute(() -> {
            try {
                h.assertTrue(h.getLevel().getRecipeManager().byKey(canonical).isEmpty(), "The implicit native output is absent from vanilla RecipeManager");
                menu.action(new RecipeTomePackets.Action(217, 2, "minecraft:paper", 0, "", output));
                var page = menu.browse("=minecraft:paper", 0);
                h.assertTrue(page.total() == 1 && page.entries().getFirst().recipe().equals(canonical)
                        && page.entries().getFirst().type().equals(ResourceLocation.withDefaultNamespace("smelting"))
                        && page.entries().getFirst().process().equals("Fixture native processor"), "Native workstation labels override vanilla recipe types and unbound Mekanism placeholders stay absent");
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
                try {
                    RecipeTomePackets.Page.CODEC.encode(buffer, page); var decoded = RecipeTomePackets.Page.CODEC.decode(buffer);
                    h.assertTrue(decoded.entries().getFirst().process().equals(page.entries().getFirst().process()) && ItemStack.isSameItemSameComponents(decoded.entries().getFirst().output(), output), "Native process and exact components survive the page protocol");
                } finally { buffer.release(); }
                h.assertTrue(menu.inscribe(canonical) && ItemStack.isSameItemSameComponents(RecipeTomeItem.product(tome, h.getLevel().registryAccess()), output), "Inscription saves only the exact native server-derived identity");
                h.assertTrue(menu.exclude(canonical, true) && !RecipeExclusions.allows(alias, RecipeTomeItem.excludedRecipes(tome)), "A native canonical ID blocks every matching planner alias");
                ItemStack forged = output.copy(); CompoundTag altered = data.copy(); altered.putBoolean("forged", true); forged.set(DataComponents.CUSTOM_DATA, CustomData.of(altered));
                menu.action(new RecipeTomePackets.Action(217, 2, "minecraft:paper", 0, "", forged));
                h.assertTrue(menu.browse("=minecraft:paper", 0).total() == 0 && !menu.inscribe(canonical), "A client-provided forged component identity cannot become native recipe knowledge");
                menu.action(new RecipeTomePackets.Action(217, 2, "minecraft:paper", 0, "", output));
                player.setPos(origin.pos().getX() + 4096.5, origin.pos().getY() + 0.5, origin.pos().getZ() + 0.5);
                h.assertTrue(NativeRecipeKnowledge.entries(player).stream().noneMatch(entry -> entry.recipe().equals(canonical)) && !menu.inscribe(canonical), "Moving away revokes implicit native inscription even when the menu has an old cached page");
                h.assertTrue(ItemStack.isSameItemSameComponents(RecipeTomeItem.product(tome, h.getLevel().registryAccess()), output), "Rejected stale or forged actions preserve the taught output");
                h.assertTrue(manager.networkAt(origin).crafting().activeJobs() == 0, "Knowledge lookup reserves and starts no crafting job");
            } finally { FIXTURES.remove(origin); }
        }).thenSucceed();
    }
}
