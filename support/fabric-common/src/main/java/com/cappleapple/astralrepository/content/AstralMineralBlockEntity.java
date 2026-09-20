package com.cappleapple.astralrepository.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Rendering identity only: no inventory, ticker, network membership, or custom persistent data. */
public final class AstralMineralBlockEntity extends BlockEntity {
    public AstralMineralBlockEntity(BlockPos pos, BlockState state) { super(AstralContent.MINERAL_ENTITY.get(), pos, state); }
}
