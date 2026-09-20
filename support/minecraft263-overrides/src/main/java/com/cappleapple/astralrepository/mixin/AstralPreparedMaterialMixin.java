package com.cappleapple.astralrepository.mixin;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PreparedRenderType.class)
abstract class AstralPreparedMaterialMixin {
    @Redirect(method="draw",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void astral$bind(RenderPass pass) {
        RenderSystem.bindDefaultUniforms(pass);
        AstralPlaneRenderType.bindPrepared(pass,(PreparedRenderType)(Object)this);
    }
}
