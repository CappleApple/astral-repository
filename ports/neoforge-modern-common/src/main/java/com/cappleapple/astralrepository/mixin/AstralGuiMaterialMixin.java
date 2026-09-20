package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
abstract class AstralGuiMaterialMixin {
    @Redirect(method="executeDraw",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void astral$bind(RenderPass pass,RenderPipeline pipeline){pass.setPipeline(pipeline);AstralPlaneRenderType.bind(pass,pipeline);}
}
