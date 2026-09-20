package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.RuneCompositeTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
public abstract class RuneCompositeGuiMixin {
    @Redirect(method="draw",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget astral$compositionTarget(GameRenderer renderer) { return RuneCompositeTarget.target(renderer.mainRenderTarget()); }
    @ModifyVariable(method="draw",at=@At("STORE"),ordinal=0)
    private WindowRenderState astral$compositionSize(WindowRenderState state) { return RuneCompositeTarget.window(state); }
}
