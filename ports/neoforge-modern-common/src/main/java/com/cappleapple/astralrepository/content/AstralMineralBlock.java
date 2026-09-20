package com.cappleapple.astralrepository.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla amethyst behavior with an unticked rendering entity. */
public class AstralMineralBlock extends AmethystBlock implements EntityBlock {
    public static final MapCodec<AstralMineralBlock> CODEC = simpleCodec(AstralMineralBlock::new);
    public AstralMineralBlock(Properties properties) { super(properties); }
    @Override public MapCodec<? extends AstralMineralBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AstralMineralBlockEntity(pos, state); }
}
