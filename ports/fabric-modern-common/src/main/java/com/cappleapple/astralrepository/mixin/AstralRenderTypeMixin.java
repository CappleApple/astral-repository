package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
abstract class AstralRenderTypeMixin {
    @Inject(method="prepare",at=@At("RETURN")) private void astral$capture(CallbackInfoReturnable<PreparedRenderType> ci){AstralPlaneRenderType.prepared((RenderType)(Object)this,ci.getReturnValue());}
    @Inject(method="canConsolidateConsecutiveGeometry",at=@At("HEAD"),cancellable=true) private void astral$keepMaterialsSeparate(CallbackInfoReturnable<Boolean> ci){if(AstralPlaneRenderType.owns((RenderType)(Object)this))ci.setReturnValue(false);}
}
