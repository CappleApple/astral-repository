package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import net.minecraft.client.renderer.DynamicUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicUniforms.class)
abstract class AstralUniformLifecycleMixin {
    @Inject(method="reset",at=@At("TAIL")) private void astral$frame(CallbackInfo ci){AstralPlaneRenderType.endFrame();}
    @Inject(method="close",at=@At("TAIL")) private void astral$close(CallbackInfo ci){AstralPlaneRenderType.close();}
}
