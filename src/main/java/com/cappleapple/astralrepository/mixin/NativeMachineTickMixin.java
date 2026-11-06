package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.crafting.NativeWorldOutputs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class NativeMachineTickMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void astral$nativeOutputOwner(BlockEntityTicker<BlockEntity> ticker, Level level, BlockPos position,
                                          BlockState state, BlockEntity entity) {
        NativeWorldOutputs.tick(level, position, () -> ticker.tick(level, position, state, entity));
    }
}
