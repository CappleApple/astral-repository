package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiRenderer.class)
abstract class AstralGuiMaterialMixin {
    @WrapOperation(method="executeDraw",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V"))
    private void astral$bind(RenderPass pass,CompiledRenderPipeline compiled,Operation<Void> original,@Local RenderPipeline pipeline) {
        original.call(pass,compiled);
        AstralPlaneRenderType.bind(pass,pipeline);
    }
}
