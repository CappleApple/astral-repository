package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/** World-dependent work is captured before the render thread consumes the frame. */
public record AstralWorldFrame(Camera camera, Frustum frustum, DeltaTracker partialTick,
        PoseStack poses, AstralBufferSource buffers) {
    private static final RenderStateDataKey<AstralBufferSource> GEOMETRY = RenderStateDataKey.create(()->"astral_repository:world_geometry");
    public Camera getCamera() { return camera; }
    public Frustum getFrustum() { return frustum; }
    public DeltaTracker getPartialTick() { return partialTick; }
    public PoseStack getPoseStack() { return poses; }
    public static void extract(LevelExtractionContext event) {
        var buffers = new AstralBufferSource();
        var frame = new AstralWorldFrame(event.camera(), event.levelState().cameraRenderState.cullFrustum, event.deltaTracker(), new PoseStack(), buffers);
        AstralWorldMaterial.begin(event.camera().position());try {
        WorldVisuals.render(frame);
        RuneRenderer.render(frame);
        BindingPreviewRenderer.render(frame);
        buffers.freeze();
        } finally { AstralPlaneRenderType.endWorld(); }
        event.levelState().setData(GEOMETRY, buffers);
    }
    public static void submit(LevelRenderContext event) {
        var buffers = event.levelState().getData(GEOMETRY);
        if (buffers != null) buffers.submit(event.poseStack(), event.submitNodeCollector());
    }
}
