package com.cappleapple.astralrepository.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla amethyst behavior with an unticked rendering entity. */
public class AstralMineralBlock extends AmethystBlock implements EntityBlock {
    public AstralMineralBlock(Properties properties) { super(properties); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AstralMineralBlockEntity(pos, state); }
}
