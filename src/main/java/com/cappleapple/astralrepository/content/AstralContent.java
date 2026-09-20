package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.AstralConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.eventbus.api.IEventBus;
import com.cappleapple.astralrepository.platform.Capabilities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class AstralContent {
    public static final String MOD_ID = "astral_repository";
    public static final java.util.Set<String> REMOVED_IDS = java.util.Set.of("astral_shard", "filter_sigil", "collection_rune", "distribution_rune",
            "routing_rune", "and_rune", "not_rune", "direction_rune", "stock_rune", "priority_rune", "push_rune", "pull_rune",
            "collection_crystal", "routing_crystal", "distribution_crystal", "buffer_crystal", "remote_crystal", "gateway_crystal", "moon_storage_crystal", "star_storage_crystal");
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK,MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final List<RegistryObject<CrystalNodeBlock>> NODES = new ArrayList<>();
    public static IntToLongFunction capacityForTier = tier -> switch (tier) {
        case 1 -> AstralConfig.seedStorageCapacity.get(); case 2 -> AstralConfig.moonStorageCapacity.get();
        case 3 -> AstralConfig.starStorageCapacity.get(); default -> AstralConfig.bufferCapacity.get();
    };
    public static final RegistryObject<CrystalNodeBlock> STORAGE_NEXUS = node("storage_nexus", NodeKind.NEXUS, 0);
    public static final RegistryObject<CrystalNodeBlock> RELAY_CRYSTAL = node("relay_crystal", NodeKind.RELAY, 0);
    public static final RegistryObject<CrystalNodeBlock> POWER_NODE = node("power_node", NodeKind.POWER, 0);
    public static final RegistryObject<CrystalNodeBlock> SEED_STORAGE_CRYSTAL = node("seed_storage_crystal", NodeKind.STORAGE, 1);
    public static final RegistryObject<AstralMineralBlock> ASTRAL_GEODE = block("astral_geode", () -> new AstralMineralBlock(BlockBehaviour.Properties.copy(Blocks.AMETHYST_BLOCK)));
    public static final RegistryObject<BuddingAstralBlock> BUDDING_ASTRAL = block("budding_astral", () -> new BuddingAstralBlock(BlockBehaviour.Properties.copy(Blocks.BUDDING_AMETHYST)));
    public static final RegistryObject<AstralClusterBlock> SMALL_ASTRAL_BUD = bud("small_astral_bud", 3, 4, Blocks.SMALL_AMETHYST_BUD);
    public static final RegistryObject<AstralClusterBlock> MEDIUM_ASTRAL_BUD = bud("medium_astral_bud", 4, 3, Blocks.MEDIUM_AMETHYST_BUD);
    public static final RegistryObject<AstralClusterBlock> LARGE_ASTRAL_BUD = bud("large_astral_bud", 5, 3, Blocks.LARGE_AMETHYST_BUD);
    public static final RegistryObject<AstralClusterBlock> ASTRAL_CLUSTER = bud("astral_cluster", 7, 3, Blocks.AMETHYST_CLUSTER);
    public static final RegistryObject<Item> ASTRAL_GEM = ITEMS.register("astral_gem",()->new com.cappleapple.astralrepository.platform.AstralItem(new Item.Properties()));
    public static final RegistryObject<BlockEntityType<AstralMineralBlockEntity>> MINERAL_ENTITY = BLOCK_ENTITIES.register("astral_mineral",
            () -> BlockEntityType.Builder.of(AstralMineralBlockEntity::new, ASTRAL_GEODE.get(), BUDDING_ASTRAL.get(), SMALL_ASTRAL_BUD.get(),
                    MEDIUM_ASTRAL_BUD.get(), LARGE_ASTRAL_BUD.get(), ASTRAL_CLUSTER.get()).build(null));
    public static final RegistryObject<AstralToolItem> ATTUNEMENT_WAND = tool("attunement_wand");
    public static final RegistryObject<AstralToolItem> RANGE_ATTUNEMENT = tool("range_attunement");
    public static final RegistryObject<AstralToolItem> MOON_ATTUNEMENT = tool("moon_attunement");
    public static final RegistryObject<AstralToolItem> STAR_ATTUNEMENT = tool("star_attunement");
    public static List<RegistryObject<CrystalNodeBlock>> activeNodes(){return List.copyOf(NODES);}
    public static final RegistryObject<DimensionalAttunementItem> DIMENSIONAL_ATTUNEMENT = ITEMS.register("dimensional_attunement", () -> new DimensionalAttunementItem(new Item.Properties()));

    public static final RegistryObject<AstralNexusItem> ASTRAL_NEXUS = ITEMS.register("astral_nexus", () -> new AstralNexusItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<RecipeTomeItem> RECIPE_TOME = ITEMS.register("recipe_tome", () -> new RecipeTomeItem(new Item.Properties().stacksTo(1)));
    public static final java.util.Optional<RegistryObject<FieldGuideItem>> FIELD_GUIDE = com.cappleapple.astralrepository.compat.PatchouliIntegration.register(ITEMS);
    public static final RegistryObject<ResonanceGogglesItem> RESONANCE_GOGGLES = ITEMS.register("resonance_goggles", () -> new ResonanceGogglesItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<CrystalNodeBlockEntity>> NODE_ENTITY = BLOCK_ENTITIES.register("crystal_node",
            () -> BlockEntityType.Builder.of(CrystalNodeBlockEntity::new, NODES.stream().map(Supplier::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = TABS.register("astral_repository", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.astral_repository")).icon(() -> new ItemStack(STORAGE_NEXUS.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().stream().filter(entry -> PowerNodeVisibility.visible(entry.get())).forEach(entry -> output.accept(entry.get()))).build());
    private AstralContent() {}
    private static RegistryObject<CrystalNodeBlock> node(String id, NodeKind kind, int tier) {
        var block = block(id, () -> new CrystalNodeBlock(BlockBehaviour.Properties.of().strength(2, 8).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 7), kind, tier));
        NODES.add(block); return block;
    }
    private static <T extends Block> RegistryObject<T> block(String id, Supplier<T> factory) {
        RegistryObject<T> result = BLOCKS.register(id, factory);
        ITEMS.register(id, () -> new com.cappleapple.astralrepository.platform.AstralBlockItem(result.get(), new Item.Properties().stacksTo(result.get() instanceof CrystalNodeBlock crystal && (crystal.kind() == NodeKind.STORAGE || crystal.kind() == NodeKind.BUFFER || crystal.kind() == NodeKind.POWER) ? 1 : 64))); return result;
    }
    private static RegistryObject<AstralClusterBlock> bud(String id, float height, float inset, Block vanilla) {
        return block(id, () -> new AstralClusterBlock(height, inset, BlockBehaviour.Properties.copy(vanilla)));
    }
    private static RegistryObject<AstralToolItem> tool(String id) {
        return ITEMS.register(id, () -> new AstralToolItem(new Item.Properties().stacksTo(id.equals("attunement_wand") ? 1 : 64), "hint.astral_repository." + id));
    }
    public static void setup(IEventBus bus) {
        MinecraftForge.EVENT_BUS.addListener(AstralContent::missingMappings);
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(RuneProgramming::interact);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void missingMappings(net.minecraftforge.registries.MissingMappingsEvent event){
        for(var mapping:event.getMappings(Registries.ITEM,MOD_ID))if(mapping.getKey().getPath().equals("bridge_attunement")||mapping.getKey().getPath().equals("remote_attunement"))mapping.remap(DIMENSIONAL_ATTUNEMENT.get());
    }
    private static void teachRecipe(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(RECIPE_TOME.get()) && event.getLevel().getBlockState(event.getPos()).is(Blocks.CRAFTING_TABLE)) {
            event.setCancellationResult(RECIPE_TOME.get().useOn(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec())));
            event.setCanceled(true);
        }
    }
}

