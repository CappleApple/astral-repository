package com.cappleapple.astralrepository.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The isolated smoke harness can exercise vanilla Shift-clicks without OS keyboard input. */
@Mixin(InputConstants.class)
public abstract class TestKeyboardMixin {
    @Inject(method="isKeyDown",at=@At("HEAD"),cancellable=true)
    private static void astral$testModifier(long window,int key,CallbackInfoReturnable<Boolean> callback) {
        if((key==341||key==345)&&Boolean.getBoolean("astral_repository.clientSmoke")&&Boolean.getBoolean("astral_repository.testControl"))callback.setReturnValue(true);
        if((key==340||key==344)&&Boolean.getBoolean("astral_repository.clientSmoke")&&Boolean.getBoolean("astral_repository.testShift"))callback.setReturnValue(true);
    }
}
