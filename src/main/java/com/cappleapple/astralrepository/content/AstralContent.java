package com.cappleapple.astralrepository.content;

import com.cappleapple.astralrepository.AstralConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.function.Supplier;
import java.util.function.Function;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AstralContent {
    public static final String MOD_ID = "astral_repository";
    public static final java.util.Set<String> REMOVED_IDS = java.util.Set.of("astral_shard", "filter_sigil", "collection_rune", "distribution_rune",
            "routing_rune", "and_rune", "not_rune", "direction_rune", "stock_rune", "priority_rune", "push_rune", "pull_rune",
            "collection_crystal", "routing_crystal", "distribution_crystal", "buffer_crystal", "remote_crystal", "gateway_crystal", "moon_storage_crystal", "star_storage_crystal");
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final List<DeferredBlock<CrystalNodeBlock>> NODES = new ArrayList<>();
    public static IntToLongFunction capacityForTier = tier -> switch (tier) {
        case 1 -> AstralConfig.seedStorageCapacity.get(); case 2 -> AstralConfig.moonStorageCapacity.get();
        case 3 -> AstralConfig.starStorageCapacity.get(); default -> AstralConfig.bufferCapacity.get();
    };
    public static final DeferredBlock<CrystalNodeBlock> STORAGE_NEXUS = node("storage_nexus", NodeKind.NEXUS, 0);
    public static final DeferredBlock<CrystalNodeBlock> RELAY_CRYSTAL = node("relay_crystal", NodeKind.RELAY, 0);
    public static final DeferredBlock<CrystalNodeBlock> POWER_NODE = node("power_node", NodeKind.POWER, 0);
    public static final DeferredBlock<CrystalNodeBlock> SEED_STORAGE_CRYSTAL = node("seed_storage_crystal", NodeKind.STORAGE, 1);
    public static final DeferredBlock<AstralMineralBlock> ASTRAL_GEODE = block("astral_geode", AstralMineralBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK));
    public static final DeferredBlock<BuddingAstralBlock> BUDDING_ASTRAL = block("budding_astral", BuddingAstralBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST));
    public static final DeferredBlock<AstralClusterBlock> SMALL_ASTRAL_BUD = bud("small_astral_bud", 3, 4, Blocks.SMALL_AMETHYST_BUD);
    public static final DeferredBlock<AstralClusterBlock> MEDIUM_ASTRAL_BUD = bud("medium_astral_bud", 4, 3, Blocks.MEDIUM_AMETHYST_BUD);
    public static final DeferredBlock<AstralClusterBlock> LARGE_ASTRAL_BUD = bud("large_astral_bud", 5, 3, Blocks.LARGE_AMETHYST_BUD);
    public static final DeferredBlock<AstralClusterBlock> ASTRAL_CLUSTER = bud("astral_cluster", 7, 3, Blocks.AMETHYST_CLUSTER);
    public static final DeferredItem<Item> ASTRAL_GEM = ITEMS.registerSimpleItem("astral_gem",props->props.trimMaterial(AstralTrims.MATERIAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AstralMineralBlockEntity>> MINERAL_ENTITY = BLOCK_ENTITIES.register("astral_mineral",
            () -> new BlockEntityType<>(AstralMineralBlockEntity::new, false, ASTRAL_GEODE.get(), BUDDING_ASTRAL.get(), SMALL_ASTRAL_BUD.get(),
                    MEDIUM_ASTRAL_BUD.get(), LARGE_ASTRAL_BUD.get(), ASTRAL_CLUSTER.get()));
    public static final DeferredItem<AstralToolItem> ATTUNEMENT_WAND = tool("attunement_wand");
    public static final DeferredItem<AstralToolItem> RANGE_ATTUNEMENT = tool("range_attunement");
    public static final DeferredItem<AstralToolItem> MOON_ATTUNEMENT = tool("moon_attunement");
    public static final DeferredItem<AstralToolItem> STAR_ATTUNEMENT = tool("star_attunement");
    public static List<DeferredBlock<CrystalNodeBlock>> activeNodes(){return List.copyOf(NODES);}
    public static final DeferredItem<DimensionalAttunementItem> DIMENSIONAL_ATTUNEMENT = ITEMS.registerItem("dimensional_attunement", props -> new DimensionalAttunementItem(props));
    static {
        var current=net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID,"dimensional_attunement");
        ITEMS.addAlias(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID,"bridge_attunement"),current);
        ITEMS.addAlias(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID,"remote_attunement"),current);
    }
    public static final DeferredItem<AstralNexusItem> ASTRAL_NEXUS = ITEMS.registerItem("astral_nexus", props -> new AstralNexusItem(props.stacksTo(1)));
    public static final DeferredItem<RecipeTomeItem> RECIPE_TOME = ITEMS.registerItem("recipe_tome", props -> new RecipeTomeItem(props.stacksTo(1)));
    public static final java.util.Optional<DeferredItem<FieldGuideItem>> FIELD_GUIDE = com.cappleapple.astralrepository.compat.PatchouliIntegration.register(ITEMS);
    public static final DeferredItem<ResonanceGogglesItem> RESONANCE_GOGGLES = ITEMS.registerItem("resonance_goggles", props -> new ResonanceGogglesItem(props.stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalNodeBlockEntity>> NODE_ENTITY = BLOCK_ENTITIES.register("crystal_node",
            () -> new BlockEntityType<>(CrystalNodeBlockEntity::new, false, NODES.stream().map(Supplier::get).toArray(Block[]::new)));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = TABS.register("astral_repository", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.astral_repository")).icon(() -> new ItemStack(STORAGE_NEXUS.get()))
            .displayItems((parameters, output) -> ITEMS.getEntries().stream().filter(entry -> PowerNodeVisibility.visible(entry.get())).forEach(entry -> output.accept(entry.get()))).build());
    private AstralContent() {}
    private static DeferredBlock<CrystalNodeBlock> node(String id, NodeKind kind, int tier) {
        var block = block(id, props -> new CrystalNodeBlock(props, kind, tier), () -> BlockBehaviour.Properties.of().strength(2, 8).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 7));
        NODES.add(block); return block;
    }
    private static <T extends Block> DeferredBlock<T> block(String id, Function<BlockBehaviour.Properties,T> factory, Supplier<BlockBehaviour.Properties> properties) {
        DeferredBlock<T> result = BLOCKS.registerBlock(id, factory, properties);
        ITEMS.registerItem(id, props -> new BlockItem(result.get(), props.useBlockDescriptionPrefix().stacksTo(result.get() instanceof CrystalNodeBlock crystal && (crystal.kind() == NodeKind.STORAGE || crystal.kind() == NodeKind.BUFFER || crystal.kind() == NodeKind.POWER) ? 1 : 64))); return result;
    }
    private static DeferredBlock<AstralClusterBlock> bud(String id, float height, float inset, Block vanilla) {
        return block(id, props -> new AstralClusterBlock(height, inset, props), () -> BlockBehaviour.Properties.ofFullCopy(vanilla));
    }
    private static DeferredItem<AstralToolItem> tool(String id) {
        return ITEMS.registerItem(id, props -> new AstralToolItem(props.stacksTo(id.equals("attunement_wand") ? 1 : 64), "hint.astral_repository." + id));
    }
    public static void setup(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus);
        bus.addListener(AstralContent::capabilities);
        NeoForge.EVENT_BUS.addListener(RuneProgramming::interact);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, NODE_ENTITY.get(), (node, side) -> node.hasInventory() ? node.inventory() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, NODE_ENTITY.get(), (node, side) -> node.hasInventory() ? node.tank() : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, NODE_ENTITY.get(), (node, side) -> node.hasInventory() ? node.energy() : null);
    }
    private static void teachRecipe(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(RECIPE_TOME.get()) && event.getLevel().getBlockState(event.getPos()).is(Blocks.CRAFTING_TABLE)) {
            event.setCancellationResult(RECIPE_TOME.get().useOn(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec())));
            event.setCanceled(true);
        }
    }
}


