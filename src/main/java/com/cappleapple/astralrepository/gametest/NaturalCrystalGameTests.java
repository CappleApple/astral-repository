package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.AstralMineralBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class NaturalCrystalGameTests {
    private static Map<Block, Block> vanillaBlocks() {
        return Map.of(AstralContent.ASTRAL_GEODE.get(), Blocks.AMETHYST_BLOCK,
                AstralContent.BUDDING_ASTRAL.get(), Blocks.BUDDING_AMETHYST,
                AstralContent.SMALL_ASTRAL_BUD.get(), Blocks.SMALL_AMETHYST_BUD,
                AstralContent.MEDIUM_ASTRAL_BUD.get(), Blocks.MEDIUM_AMETHYST_BUD,
                AstralContent.LARGE_ASTRAL_BUD.get(), Blocks.LARGE_AMETHYST_BUD,
                AstralContent.ASTRAL_CLUSTER.get(), Blocks.AMETHYST_CLUSTER);
    }

    @GameTest(template="empty_workshop")
    public static void buddingGrowsEveryVanillaStageAndPreservesWater(GameTestHelper h) {
        BlockPos base = new BlockPos(8, 3, 8);
        h.setBlock(base, AstralContent.BUDDING_ASTRAL.get());
        for (Direction direction : Direction.values()) h.setBlock(base.relative(direction), direction.getAxis().isHorizontal() ? Blocks.WATER : Blocks.AIR);
        RandomSource random = RandomSource.create(418);
        boolean[] saw = new boolean[4];
        for (int tick = 0; tick < 3000; tick++) {
            h.getBlockState(base).randomTick(h.getLevel(), h.absolutePos(base), random);
            for (Direction direction : Direction.values()) {
                BlockState grown = h.getBlockState(base.relative(direction));
                if (grown.is(AstralContent.SMALL_ASTRAL_BUD.get())) saw[0] = true;
                if (grown.is(AstralContent.MEDIUM_ASTRAL_BUD.get())) saw[1] = true;
                if (grown.is(AstralContent.LARGE_ASTRAL_BUD.get())) saw[2] = true;
                if (grown.is(AstralContent.ASTRAL_CLUSTER.get())) saw[3] = true;
            }
        }
        for (boolean stage : saw) h.assertTrue(stage, "Growth visits every vanilla stage");
        for (Direction direction : Direction.values()) {
            BlockPos at = base.relative(direction);
            BlockState grown = h.getBlockState(at);
            h.assertTrue(grown.is(AstralContent.ASTRAL_CLUSTER.get()), "Every supported face matures");
            h.assertTrue(grown.getValue(AmethystClusterBlock.FACING) == direction, "Growth retains facing");
            h.assertTrue(grown.getValue(AmethystClusterBlock.WATERLOGGED) == direction.getAxis().isHorizontal(), "Growth retains waterlogging");
            h.assertTrue(h.getBlockEntity(at) instanceof AstralMineralBlockEntity, "Every growth stage has its rendering identity");
        }
        h.setBlock(base, Blocks.AIR);
        for (Direction direction : Direction.values()) h.assertTrue(!h.getBlockState(base.relative(direction)).is(AstralContent.ASTRAL_CLUSTER.get()), "Removing support breaks the cluster");
        h.succeed();
    }

    @GameTest(template="empty_workshop")
    public static void naturalShapesLightHardnessAndPistonsMatchAmethyst(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(3, 3, 3));
        for (var entry : vanillaBlocks().entrySet()) {
            for (Direction direction : Direction.values()) {
                BlockState actual = entry.getKey().defaultBlockState();
                BlockState vanilla = entry.getValue().defaultBlockState();
                if (actual.hasProperty(AmethystClusterBlock.FACING)) {
                    actual = actual.setValue(AmethystClusterBlock.FACING, direction);
                    vanilla = vanilla.setValue(AmethystClusterBlock.FACING, direction);
                }
                h.assertTrue(actual.getShape(h.getLevel(), at).toAabbs().equals(vanilla.getShape(h.getLevel(), at).toAabbs()), "Collision dimensions match vanilla in every direction");
                h.assertTrue(actual.getLightEmission() == vanilla.getLightEmission(), "Growth-stage light matches vanilla");
                h.assertTrue(actual.getDestroySpeed(h.getLevel(), at) == vanilla.getDestroySpeed(h.getLevel(), at), "Hardness matches vanilla");
                h.assertTrue(actual.getPistonPushReaction() == vanilla.getPistonPushReaction(), "Piston response matches vanilla");
                h.assertTrue(actual.requiresCorrectToolForDrops() == vanilla.requiresCorrectToolForDrops(), "Tool requirements match vanilla");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty_workshop")
    public static void gemHarvestSilkTouchAndUnobtainableBuddingMatchVanilla(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(4, 3, 4));
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack silk = pick.copy();
        silk.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        BlockState cluster = AstralContent.ASTRAL_CLUSTER.get().defaultBlockState();
        List<ItemStack> bare = Block.getDrops(cluster, h.getLevel(), at, null, null, ItemStack.EMPTY);
        h.assertTrue(bare.size() == 1 && bare.getFirst().is(AstralContent.ASTRAL_GEM.get()) && bare.getFirst().getCount() == 2, "Ordinary harvesting drops two Astral Gems");
        List<ItemStack> mined = Block.getDrops(cluster, h.getLevel(), at, null, null, pick);
        h.assertTrue(mined.size() == 1 && mined.getFirst().is(AstralContent.ASTRAL_GEM.get()) && mined.getFirst().getCount() == 4, "A tagged harvest tool drops four Astral Gems");
        for (Block block : List.of(AstralContent.SMALL_ASTRAL_BUD.get(), AstralContent.MEDIUM_ASTRAL_BUD.get(), AstralContent.LARGE_ASTRAL_BUD.get(), AstralContent.ASTRAL_CLUSTER.get())) {
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), h.getLevel(), at, null, null, silk);
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()), "Silk Touch preserves buds and clusters");
            if (block != AstralContent.ASTRAL_CLUSTER.get()) h.assertTrue(Block.getDrops(block.defaultBlockState(), h.getLevel(), at, null, null, pick).isEmpty(), "Immature buds drop nothing without Silk Touch");
        }
        h.assertTrue(Block.getDrops(AstralContent.BUDDING_ASTRAL.get().defaultBlockState(), h.getLevel(), at, null, null, silk).isEmpty(), "Budding blocks remain unobtainable even with Silk Touch");
        h.succeed();
    }

    @GameTest(template="empty_workshop", timeoutTicks=100)
    public static void solidAstralGemBlockCanBePushedWithoutLosingItsRenderer(GameTestHelper h) {
        BlockPos piston = new BlockPos(4, 3, 4);
        BlockPos source = piston.east(), destination = piston.east(2);
        h.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.EAST));
        h.setBlock(source, AstralContent.ASTRAL_GEODE.get());
        for (Direction direction : Direction.values()) h.assertTrue(net.minecraft.world.level.block.piston.PistonBaseBlock.isPushable(
                h.getBlockState(source), h.getLevel(), h.absolutePos(source), direction, false, direction), "Stateless mineral remains pushable like an amethyst block");
        h.setBlock(piston.below(), Blocks.REDSTONE_BLOCK);
        h.succeedWhen(() -> {
            h.assertTrue(h.getBlockState(destination).is(AstralContent.ASTRAL_GEODE.get()), "Real piston moves the Astral Gem block");
            h.assertTrue(h.getBlockEntity(destination) instanceof AstralMineralBlockEntity, "Settled moving block recreates its renderer identity");
            h.assertTrue(h.getBlockState(source).is(Blocks.PISTON_HEAD), "The old block position is occupied only by the piston head");
        });
    }

    @GameTest(template="empty_workshop")
    public static void legacyMineralStateGetsRendererIdentityAndChunkPacket(GameTestHelper h) {
        BlockPos relative = new BlockPos(7, 3, 7);
        h.setBlock(relative, AstralContent.ASTRAL_GEODE.get());
        BlockPos pos = h.absolutePos(relative);
        var chunk = h.getLevel().getChunkAt(pos);
        chunk.removeBlockEntity(pos);
        h.assertTrue(chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) == null, "Legacy fixture has a natural state but no saved renderer identity");
        com.cappleapple.astralrepository.content.AstralMineralMigration.loaded(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, false));
        h.succeedWhen(() -> {
            h.assertTrue(chunk.getBlockEntity(pos) instanceof AstralMineralBlockEntity, "Chunk-load migration creates the missing identity");
            var packet = new net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData(chunk);
            var received = new net.minecraft.world.level.chunk.LevelChunk(h.getLevel(), chunk.getPos());
            received.replaceWithPacketData(packet.getReadBuffer(), packet.getHeightmaps(), packet.getBlockEntitiesTagsConsumer(chunk.getPos().x, chunk.getPos().z));
            h.assertTrue(received.getBlockState(pos).is(AstralContent.ASTRAL_GEODE.get()), "Chunk packet retains the original natural block state");
            h.assertTrue(received.getBlockEntity(pos) instanceof AstralMineralBlockEntity, "Normal chunk-packet application recreates the renderer identity");
            h.assertTrue(com.cappleapple.astralrepository.content.AstralMineralMigration.repair(chunk) == 0, "Repeated migration does not replace existing block entities");
        });
    }

    @GameTest(template="empty_geode", timeoutTicks=300)
    public static void generatedGeodeExactlyMatchesVanillaShellAndGrowthPlacement(GameTestHelper h) {
        var level = h.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var astral = registry.get(Identifier.fromNamespaceAndPath("astral_repository", "astral_geode"));
        var vanilla = registry.get(Identifier.withDefaultNamespace("amethyst_geode"));
        h.assertTrue(astral != null && vanilla != null, "Both configured geodes are loaded");
        BlockPos origin = h.absolutePos(new BlockPos(20, 20, 20));
        Map<BlockPos, BlockState> actual = new HashMap<>();
        for (BlockPos relative : BlockPos.betweenClosed(3, 3, 3, 37, 37, 37)) level.setBlock(h.absolutePos(relative), Blocks.STONE.defaultBlockState(), 2);
        h.assertTrue(astral.place(level, level.getChunkSource().getGenerator(), RandomSource.create(1861), origin), "Astral geode actually generates");
        for (BlockPos relative : BlockPos.betweenClosed(3, 3, 3, 37, 37, 37)) actual.put(relative.immutable(), level.getBlockState(h.absolutePos(relative)));
        h.assertTrue(actual.values().stream().anyMatch(state -> state.is(AstralContent.BUDDING_ASTRAL.get())), "Real generation includes renewable budding blocks");
        h.assertTrue(actual.values().stream().anyMatch(state -> state.is(Blocks.CALCITE)) && actual.values().stream().anyMatch(state -> state.is(Blocks.SMOOTH_BASALT)), "Real generation includes the calcite and basalt shell");
        for (BlockPos relative : actual.keySet()) level.setBlock(h.absolutePos(relative), Blocks.STONE.defaultBlockState(), 2);
        h.assertTrue(vanilla.place(level, level.getChunkSource().getGenerator(), RandomSource.create(1861), origin), "Vanilla reference geode generates with the same seed");
        Map<Block, Block> replacements = vanillaBlocks();
        for (var entry : actual.entrySet()) {
            BlockState expected = entry.getValue();
            Block replacement = replacements.get(expected.getBlock());
            if (replacement != null) {
                BlockState converted = replacement.defaultBlockState();
                if (expected.hasProperty(AmethystClusterBlock.FACING)) converted = converted.setValue(AmethystClusterBlock.FACING, expected.getValue(AmethystClusterBlock.FACING)).setValue(AmethystClusterBlock.WATERLOGGED, expected.getValue(AmethystClusterBlock.WATERLOGGED));
                expected = converted;
            }
            h.assertTrue(level.getBlockState(h.absolutePos(entry.getKey())).equals(expected), "Generated shell, budding distribution, and bud positions match vanilla at " + entry.getKey());
        }
        h.succeed();
    }
}
