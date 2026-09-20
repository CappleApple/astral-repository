package com.cappleapple.astralrepository.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import com.mojang.blaze3d.systems.RenderSystem;
import com.cappleapple.astralrepository.platform.client.event.RenderLevelStageEvent;

/** Fancy clouds write world depth; Fabulous instead sorts separate targets by their depth. */
public final class TransferRenderPass {
    // Fabulous sorts particle color against clouds using this depth texture. In Fast/Fancy,
    // keep soft sprites color-only because the cloud pass already populated world depth.
    static final RenderStateShard.WriteMaskStateShard SPRITE_WRITE=new RenderStateShard.WriteMaskStateShard(true,false) {
        @Override public void setupRenderState() {
            super.setupRenderState();
            RenderSystem.depthMask(Minecraft.useShaderTransparency());
        }
    };
    public static boolean matches(RenderLevelStageEvent event) {
        return event.getStage() == stage();
    }
    public static RenderLevelStageEvent.Stage stage() {
        return Minecraft.useShaderTransparency()
                ? RenderLevelStageEvent.Stage.AFTER_PARTICLES : RenderLevelStageEvent.Stage.AFTER_WEATHER;
    }
    private TransferRenderPass() {}
}
