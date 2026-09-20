package com.cappleapple.astralrepository.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public final class BuddingAstralBlock extends AstralMineralBlock {
    public static final MapCodec<BuddingAstralBlock> CODEC = simpleCodec(BuddingAstralBlock::new);
    public BuddingAstralBlock(Properties properties) { super(properties); }
    @Override public MapCodec<BuddingAstralBlock> codec() { return CODEC; }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        Direction direction = Direction.getRandom(random); BlockPos next = pos.relative(direction);
        BlockState previous = level.getBlockState(next); net.minecraft.world.level.block.Block growth = null;
        if (BuddingAmethystBlock.canClusterGrowAtState(previous)) growth = AstralContent.SMALL_ASTRAL_BUD.get();
        else if (previous.is(AstralContent.SMALL_ASTRAL_BUD.get()) && previous.getValue(AmethystClusterBlock.FACING) == direction) growth = AstralContent.MEDIUM_ASTRAL_BUD.get();
        else if (previous.is(AstralContent.MEDIUM_ASTRAL_BUD.get()) && previous.getValue(AmethystClusterBlock.FACING) == direction) growth = AstralContent.LARGE_ASTRAL_BUD.get();
        else if (previous.is(AstralContent.LARGE_ASTRAL_BUD.get()) && previous.getValue(AmethystClusterBlock.FACING) == direction) growth = AstralContent.ASTRAL_CLUSTER.get();
        if (growth != null) level.setBlockAndUpdate(next, growth.defaultBlockState().setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, previous.getFluidState().is(Fluids.WATER)));
    }
}
