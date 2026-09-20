package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.AstralInterfaceRenderer;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Actual GPU panel comparisons, available only in the excluded development smoke client. */
public final class InterfaceMaterialSmoke {
    private static final int SIZE = 384, X = 24, Y = 24, WIDTH = 318, HEIGHT = 266;
    private static final ResourceLocation PANEL = new ResourceLocation(AstralRepository.MOD_ID, "textures/gui/nexus.png");
    private InterfaceMaterialSmoke() {}

    public static void verify() throws Exception {
        check(Boolean.getBoolean("astral_repository.clientSmoke"), "Interface fixture requires the development smoke client");
        RenderSystem.assertOnRenderThread();
        check(AstralPlaneRenderType.ready(), "Astral interface shader is not loaded");
        Path output = Path.of("../build/client-smoke/interface");
        Files.createDirectories(output);
        Minecraft minecraft = Minecraft.getInstance();
        double previousInterface = AstralClientConfig.astralInterfaceOverlayOpacity.get();
        double previousWorld = AstralClientConfig.astralOverlayOpacity.get();
        var previousShader = RenderSystem.getShader();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        float time = RenderSystem.getShaderGameTime(), fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderColor().clone(), clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        int[] viewport = new int[4], scissorBox = new int[4], colorMask = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox); GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int[] textures = new int[3], bindings = new int[3];
        for (int unit = 0; unit < 3; unit++) { textures[unit] = RenderSystem.getShaderTexture(unit); RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit); bindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
        RenderSystem.activeTexture(activeTexture);
        var panelTexture = minecraft.getTextureManager().getTexture(PANEL);
        panelTexture.setBlurMipmap(false, false);
        RenderSystem.getModelViewStack().pushMatrix();
        TextureTarget target = null;
        try (ByteBufferBuilder memory = new ByteBufferBuilder(4096)) {
            RenderSystem.disableScissor(); RenderSystem.depthMask(true); RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest(); RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.setShaderFogStart(1000); RenderSystem.setShaderFogEnd(2000);
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            GuiGraphics graphics = new GuiGraphics(minecraft, MultiBufferSource.immediate(memory));
            try (NativeImage raw = NativeImage.read(minecraft.getResourceManager().open(PANEL));
                 NativeImage vanilla = render(target, graphics, true, 0, 1, 4200, 0);
                 NativeImage zero = render(target, graphics, false, 0, 1, 4200, 0);
                 NativeImage standard = render(target, graphics, false, .24, 0, 4200, 0);
                 NativeImage otherWorld = render(target, graphics, false, .24, 1, 4200, 0);
                 NativeImage full = render(target, graphics, false, 1, 1, 4200, 0);
                 NativeImage drift = render(target, graphics, false, .24, 1, 4440, 0);
                 NativeImage translated = render(target, graphics, false, .24, 1, 4200, 32)) {
                int rawMismatches = 0, opaquePixels = 0;
                check(raw.getWidth() == WIDTH && raw.getHeight() == HEIGHT, "Nexus panel source dimensions changed");
                for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                    int original = raw.getPixelRGBA(x, y);
                    if ((original >>> 24) == 255) { opaquePixels++; if (original != vanilla.getPixelRGBA(X + x, Y + y)) rawMismatches++; }
                }
                double zeroDifference = difference(vanilla, zero), independentDifference = difference(standard, otherWorld);
                double fullDifference = difference(zero, full), defaultDifference = difference(zero, standard);
                double movement = difference(standard, drift), shiftedDifference = difference(standard, translated);
                int movingPixels = changedPixels(standard, drift, .01);
                vanilla.writeToFile(output.resolve("panel_vanilla.png")); zero.writeToFile(output.resolve("panel_opacity_zero.png"));
                standard.writeToFile(output.resolve("panel_default.png")); full.writeToFile(output.resolve("panel_opacity_full.png"));
                drift.writeToFile(output.resolve("panel_12_second_drift.png")); translated.writeToFile(output.resolve("panel_compensated_translation.png"));
                String report = "Actual GPU GUI panel; 318x266 original PNG, 384x384 FBO, GameTime4200/4440 ticks.\n"
                        + "opaque_png_pixels=" + opaquePixels + "\nraw_png_mismatches=" + rawMismatches
                        + "\nopacity0_vanilla_difference=" + zeroDifference + "\nworld_opacity_independence=" + independentDifference
                        + "\nopacity024_difference=" + defaultDifference + "\nopacity1_difference=" + fullDifference
                        + "\n12_second_drift=" + movement + "\nmoving_pixels_over_001=" + movingPixels
                        + "\ncompensated_translation=" + shiftedDifference + "\n";
                Files.writeString(output.resolve("interface_metrics.txt"), report);
                AstralRepository.LOGGER.info("Interface GPU measurements before assertions:\n{}", report);
                check(opaquePixels > 10000 && rawMismatches == 0, "Raw panel artwork did not reproduce exactly: " + rawMismatches);
                check(zeroDifference == 0, "Zero GUI opacity differs from vanilla PNG blit: " + zeroDifference);
                check(independentDifference == 0, "World opacity changed the interface framebuffer: " + independentDifference);
                check(defaultDifference > .005 && fullDifference > defaultDifference * 2, "Interface opacity does not visibly control the field");
                check(movement > .0005 && movingPixels > 80, "Interface field lacks visible twelve-second motion: " + movement + ", pixels=" + movingPixels);
                check(shiftedDifference < .0001, "Compensated view translation moved the GUI field: " + shiftedDifference);
                check(AstralPlaneRenderType.appliedInterfaceMode() && Math.abs(AstralPlaneRenderType.appliedInterfaceOverlayOpacity() - .24F) < .0001F, "Interface draw did not apply its own material state");
                AstralClientConfig.astralOverlayOpacity.set(.63);
                AstralPlaneRenderType.ASTRAL_OVERLAY.setupRenderState();
                try {
                    check(!AstralPlaneRenderType.appliedInterfaceMode() && Math.abs(AstralPlaneRenderType.appliedOverlayOpacity() - .63F) < .0001F,
                            "World material inherited the preceding interface mode or opacity");
                    check(RenderSystem.getShader().getUniform("AstralInterfaceMode").getFloatBuffer().get(0) == 0F,
                            "World material did not reset the actual interface shader uniform");
                } finally { AstralPlaneRenderType.ASTRAL_OVERLAY.clearRenderState(); }
                Files.writeString(output.resolve("result.txt"), "PASS: exact PNG at0; independent GUI/world opacity; visible timed motion; stable surface anchoring; world material reset.\n");
            }
        } finally {
            AstralClientConfig.astralInterfaceOverlayOpacity.set(previousInterface); AstralClientConfig.astralOverlayOpacity.set(previousWorld);
            // Restore the shared shader's world uniforms, then restore the actual previous GL state.
            AstralPlaneRenderType.ASTRAL_OVERLAY.setupRenderState(); AstralPlaneRenderType.ASTRAL_OVERLAY.clearRenderState();
            if (target != null) target.destroyBuffers();
            RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderGameTime(0, time * 24000); RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]); RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            panelTexture.restoreLastBlurMipmap(); RenderSystem.setShader(() -> previousShader);
            for (int unit = 0; unit < 3; unit++) { RenderSystem.setShaderTexture(unit, textures[unit]); RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit); RenderSystem.bindTexture(bindings[unit]); }
            RenderSystem.activeTexture(activeTexture); RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(depthMask);
            RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]); else RenderSystem.disableScissor();
            BufferUploader.reset(); GL30.glBindVertexArray(vao); GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer); GlStateManager._glUseProgram(program);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer); GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static NativeImage render(TextureTarget target, GuiGraphics graphics, boolean vanilla, double opacity, double worldOpacity, long time, int shift) {
        AstralClientConfig.astralInterfaceOverlayOpacity.set(opacity); AstralClientConfig.astralOverlayOpacity.set(worldOpacity);
        target.setClearColor(.02F, .03F, .05F, 1); RenderSystem.depthMask(true); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
        RenderSystem.getModelViewStack().identity().translate(-shift, 0, 0); RenderSystem.applyModelViewMatrix();
        Matrix4f projection = new Matrix4f().setOrtho(0, SIZE, SIZE, 0, -100, 100);
        projection.m30(projection.m30() + projection.m00() * shift);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z); RenderSystem.setShaderGameTime(time, 0);
        RenderSystem.disableDepthTest(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        if (vanilla) graphics.blit(PANEL, X, Y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        else AstralInterfaceRenderer.blit(graphics, PANEL, X, Y, WIDTH, HEIGHT);
        graphics.flush();
        return Screenshot.takeScreenshot(target);
    }
    private static double difference(NativeImage first, NativeImage second) {
        double sum = 0;
        for (int y = Y; y < Y + HEIGHT; y++) for (int x = X; x < X + WIDTH; x++) sum += delta(first.getPixelRGBA(x, y), second.getPixelRGBA(x, y));
        return sum / (WIDTH * HEIGHT);
    }
    private static int changedPixels(NativeImage first, NativeImage second, double threshold) {
        int changed = 0;
        for (int y = Y; y < Y + HEIGHT; y++) for (int x = X; x < X + WIDTH; x++) if (delta(first.getPixelRGBA(x, y), second.getPixelRGBA(x, y)) > threshold) changed++;
        return changed;
    }
    private static double delta(int first, int second) {
        return (Math.abs((first & 255) - (second & 255)) + Math.abs(((first >>> 8) & 255) - ((second >>> 8) & 255)) + Math.abs(((first >>> 16) & 255) - ((second >>> 16) & 255))) / (3.0 * 255);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
