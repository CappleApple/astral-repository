package com.cappleapple.astralrepository.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps vanilla attachment, waterlogging, collision, sounds, and piston behavior. */
public final class AstralClusterBlock extends AmethystClusterBlock implements EntityBlock {
    public AstralClusterBlock(float height, float inset, Properties properties) { super(height, inset, properties); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AstralMineralBlockEntity(pos, state); }
}
