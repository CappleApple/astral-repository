package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Draws an entire panel PNG through the shared astral material before its controls. */
public final class AstralInterfaceRenderer {
    private AstralInterfaceRenderer() {}

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        if (!AstralPlaneRenderType.ready() || AstralClientConfig.astralInterfaceOverlayOpacity.get() <= 0.0) {
            graphics.blit(texture, x, y, 0, 0, width, height, width, height);
            return;
        }
        // Finish earlier GUI work before changing shader uniforms; finish this panel before
        // opaque controls, text, and item renderers use their own materials.
        graphics.flush();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(AstralPlaneRenderType.interfaceMaterial(texture));
        Matrix4f transform = graphics.pose().last().pose();
        vertex(vertices, transform, x, y + height, 0, 1);
        vertex(vertices, transform, x + width, y + height, 1, 1);
        vertex(vertices, transform, x + width, y, 1, 0);
        vertex(vertices, transform, x, y, 0, 0);
        graphics.flush();
    }

    private static void vertex(VertexConsumer vertices, Matrix4f transform, float x, float y, float u, float v) {
        vertices.addVertex(transform, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
    }
}
