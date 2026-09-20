package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.content.AstralContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class AstralMineralPistonMixin {
    @Inject(method = "isPushable", at = @At("RETURN"), cancellable = true)
    private static void astralRepository$statelessMineral(BlockState state, Level level, BlockPos pos, Direction movement,
                                                         boolean allowDestroy, Direction pistonFacing, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValue() && state.is(AstralContent.ASTRAL_GEODE.get())) {
            // The only entity is a replaceable rendering identity; retain every vanilla boundary/direction check.
            result.setReturnValue(PistonBaseBlock.isPushable(Blocks.AMETHYST_BLOCK.defaultBlockState(), level, pos, movement, allowDestroy, pistonFacing));
        }
    }
}
