package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** World-dependent work is captured before the render thread consumes the frame. */
@EventBusSubscriber(modid="astral_repository", value=Dist.CLIENT)
public record AstralWorldFrame(Camera camera, Frustum frustum, DeltaTracker partialTick,
        PoseStack poses, AstralBufferSource buffers) {
    private static final ContextKey<AstralBufferSource> GEOMETRY = new ContextKey<>(Identifier.fromNamespaceAndPath("astral_repository", "world_geometry"));
    public Camera getCamera() { return camera; }
    public Frustum getFrustum() { return frustum; }
    public DeltaTracker getPartialTick() { return partialTick; }
    public PoseStack getPoseStack() { return poses; }
    @SubscribeEvent public static void extract(ExtractLevelRenderStateEvent event) {
        var buffers = new AstralBufferSource();
        var frame = new AstralWorldFrame(event.getCamera(), event.getFrustum(), event.getDeltaTracker(), new PoseStack(), buffers);
        AstralWorldMaterial.begin(event.getCamera().position());try {
        WorldVisuals.render(frame);
        RuneRenderer.render(frame);
        BindingPreviewRenderer.render(frame);
        buffers.freeze();
        } finally { AstralPlaneRenderType.endWorld(); }
        event.getRenderState().setRenderData(GEOMETRY, buffers);
    }
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event) {
        var buffers = event.getLevelRenderState().getRenderData(GEOMETRY);
        if (buffers != null) buffers.submit(event.getPoseStack(), event.getSubmitNodeCollector());
    }
}
