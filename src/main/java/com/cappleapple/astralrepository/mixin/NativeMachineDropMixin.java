package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.crafting.NativeWorldOutputs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class NativeMachineDropMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void astral$recordNativeDrop(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (Boolean.TRUE.equals(result.getReturnValue()) && entity instanceof ItemEntity item) NativeWorldOutputs.spawned(item);
    }
}
