package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

/** Excluded development fixture: calls the loaded BNS rail and toolbar through reflection. */
public final class BundledTabSmoke {
    private static final int SIZE = 96, X = 24, Y = 16, WIDTH = 21, HEIGHT = 64, BUTTON = 13;
    private static final Identifier CONTROLS = Identifier.fromNamespaceAndPath(AstralRepository.MOD_ID, "textures/gui/nexus_controls.png");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AstralRepository.MOD_ID, "textures/gui/bundled_tab.png");
    private BundledTabSmoke() {}

    public static void verify(NexusScreen screen) throws Exception {
        check(Boolean.getBoolean("astral_repository.clientSmoke"), "BNS fixture requires the development smoke client");
        RenderSystem.assertOnRenderThread();
        check(ModList.get().isLoaded("bundlednotsiloed") && ModList.get().isLoaded("stacksnotslots"), "BNS and Stacks Not Slots must actually be loaded");
        Minecraft minecraft = Minecraft.getInstance();
        check(minecraft.screen == screen && minecraft.player != null, "The initialized Nexus must remain open for the BNS fixture");
        check(AstralPlaneRenderType.ready(), "Astral interface shader is not loaded");
        Class<?> overlay = Class.forName("com.cappleapple.bundlednotsiloed.client.ContainerInventoryOverlay");
        Object grid = field(overlay, "activeGrid").get(null);
        check(field(overlay, "activeScreen").get(null) == screen && grid != null, "BNS has not established the real Nexus inventory grid");
        check(!(boolean)field(overlay, "searchFocused").get(null), "BNS search must not be focused in this background fixture");
        Object rail = method(grid.getClass(), "rail").invoke(grid);
        Class<?> railClass = rail.getClass();
        Method background = method(railClass, "renderBackground", GuiGraphics.class);
        Method toolbar = method(overlay, "renderToolbar", GuiGraphics.class, int.class, int.class);
        Bounds bounds = bounds(rail);
        check(bounds.width == WIDTH && bounds.height == HEIGHT, "Expected actual21x64 BNS rail, got " + bounds);
        int buttonX = coordinate(rail, "buttonX") - bounds.left;
        int buttonTop = coordinate(rail, "buttonTop") - bounds.top;
        check(buttonX >= 0 && buttonX + BUTTON <= WIDTH && buttonTop == 3, "BNS button coordinates no longer fit the side rail");
        Class<?> sideRail = Class.forName("com.cappleapple.bundlednotsiloed.client.InventorySideRail");
        Object narrowRail = method(sideRail, "at", int.class, int.class).invoke(null, 4, 3);
        Bounds narrow = bounds(narrowRail);
        check(narrow.left == 0 && narrow.width == 8 && narrow.height == HEIGHT, "Narrow rail fixture no longer exercises the clipped left edge");
        Path output = Path.of("../build/client-smoke/bundled-tab");
        Files.createDirectories(output);
        Screen nonNexus = new Screen(Component.literal("BNS native scope fixture")) {};
        Screen oldScreen = minecraft.screen;
        double oldOpacity = AstralClientConfig.astralInterfaceOverlayOpacity.get();
        GlSnapshot gl = new GlSnapshot();
        TextureTarget target = null;
        Map<String, NativeImage> images = new LinkedHashMap<>();
        var texture = minecraft.getTextureManager().getTexture(TEXTURE);
        texture.setBlurMipmap(false, false);
        RenderSystem.getModelViewStack().pushMatrix();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(4096);
             NativeImage raw = NativeImage.read(minecraft.getResourceManager().open(TEXTURE))) {
            RenderSystem.disableScissor(); RenderSystem.depthMask(true); RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest(); RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.setShaderFogStart(1000); RenderSystem.setShaderFogEnd(2000);
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            GuiGraphics graphics = new GuiGraphics(minecraft, MultiBufferSource.immediate(memory));
            Capture capture = new Capture(target, graphics, background, toolbar, rail, bounds, output, images);
            NativeImage expected = capture.draw("expected_custom_png", screen, 0, true, false);
            NativeImage zero = capture.draw("nexus_background_zero", screen, 0, false, false);
            NativeImage standard = capture.draw("nexus_background_default", screen, .24, false, false);
            NativeImage full = capture.draw("nexus_background_full", screen, 1, false, false);
            NativeImage nativeZero = capture.draw("native_background_zero", nonNexus, 0, false, false);
            NativeImage nativeFull = capture.draw("native_background_full", nonNexus, 1, false, false);
            NativeImage toolbarZero = capture.draw("nexus_toolbar_zero", screen, 0, false, true);
            NativeImage toolbarDefault = capture.draw("nexus_toolbar_default", screen, .24, false, true);
            NativeImage toolbarFull = capture.draw("nexus_toolbar_full", screen, 1, false, true);
            NativeImage nativeToolbarZero = capture.draw("native_toolbar_zero", nonNexus, 0, false, true);
            NativeImage nativeToolbarFull = capture.draw("native_toolbar_full", nonNexus, 1, false, true);
            Capture clipped = new Capture(target, graphics, background, toolbar, narrowRail, narrow, output, images);
            NativeImage narrowExpected = clipped.draw("narrow_expected_png", screen, 0, true, false);
            NativeImage narrowZero = clipped.draw("narrow_background_zero", screen, 0, false, false);
            NativeImage narrowFull = clipped.draw("narrow_background_full", screen, 1, false, false);
            int rawMismatches = 0, opaque = 0, shapeMismatches = 0;
            check(raw.getWidth() == WIDTH && raw.getHeight() == HEIGHT, "Custom BNS PNG must be21x64");
            int clear = zero.getPixelRGBA(0, 0);
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                int pixel = raw.getPixelRGBA(x, y);
                if ((pixel >>> 24) == 255) { opaque++; if (zero.getPixelRGBA(X + x, Y + y) != pixel) rawMismatches++; }
                else if ((pixel >>> 24) == 0 && (zero.getPixelRGBA(X + x, Y + y) != clear || full.getPixelRGBA(X + x, Y + y) != clear)) shapeMismatches++;
            }
            int buttonMismatches = 0, nativeButtonMismatches = 0;
            for (int button = 0; button < 4; button++) for (int y = 0; y < BUTTON; y++) for (int x = 0; x < BUTTON; x++) {
                int px = X + buttonX + x, py = Y + buttonTop + button * 15 + y;
                int original = toolbarZero.getPixelRGBA(px, py);
                if (original != toolbarDefault.getPixelRGBA(px, py) || original != toolbarFull.getPixelRGBA(px, py)) buttonMismatches++;
                if (original != nativeToolbarZero.getPixelRGBA(px, py) || original != nativeToolbarFull.getPixelRGBA(px, py)) nativeButtonMismatches++;
            }
            double defaultDifference = difference(zero, standard, WIDTH, HEIGHT), fullDifference = difference(zero, full, WIDTH, HEIGHT);
            int outside = outsideChanges(zero, full, WIDTH, HEIGHT), narrowOutside = outsideChanges(narrowZero, narrowFull, narrow.width, narrow.height);
            String highlightReport = verifyHighlights(capture, overlay, screen, nonNexus);
            String report = "Loaded BNS " + version("bundlednotsiloed") + ", SNS " + version("stacksnotslots") + "; real active Nexus grid and private renderToolbar;96x96 GPU FBO.\n"
                    + "actual_rail=" + bounds + "\nbutton_rectangles=4x13x13\nnarrow_rail=" + narrow
                    + "\nraw_opaque_pixels=" + opaque + "\nraw_png_mismatches=" + rawMismatches + "\ntransparent_shape_mismatches=" + shapeMismatches
                    + "\nzero_expected_mismatches=" + mismatches(expected, zero) + "\ndefault_background_difference=" + defaultDifference + "\nfull_background_difference=" + fullDifference
                    + "\nbutton_opacity_mismatches=" + buttonMismatches + "\nnative_button_mismatches=" + nativeButtonMismatches
                    + "\nnative_opacity_mismatches=" + mismatches(nativeZero, nativeFull) + "\nnative_style_difference=" + difference(nativeZero, zero, WIDTH, HEIGHT)
                    + "\nchanged_pixels_outside_background=" + outside + "\nnarrow_zero_mismatches=" + mismatches(narrowExpected, narrowZero)
                    + "\nnarrow_changed_pixels_outside_background=" + narrowOutside + "\n" + highlightReport;
            Files.writeString(output.resolve("metrics.txt"), report);
            AstralRepository.LOGGER.info("BNS tab GPU measurements before assertions:\n{}", report);
            check(opaque > 1300 && rawMismatches == 0 && shapeMismatches == 0, "Custom rail artwork or transparent edge changed at zero opacity");
            check(mismatches(expected, zero) == 0, "Nexus zero-opacity rail differs from the exact custom PNG blit");
            check(defaultDifference > .002 && fullDifference > defaultDifference * 1.6, "BNS rail opacity does not visibly control its background");
            check(buttonMismatches == 0 && nativeButtonMismatches == 0, "BNS native13x13 buttons changed with the Nexus styling");
            check(mismatches(nativeZero, nativeFull) == 0 && difference(nativeZero, zero, WIDTH, HEIGHT) > .1,
                    "A non-Nexus screen inherited the Astral background or opacity");
            check(rgb(nativeZero.getPixelRGBA(X + 2, Y)) == 0x373737 && rgb(nativeZero.getPixelRGBA(X + 4, Y + 4)) == 0xc6c6c6,
                    "Non-Nexus rail did not render BNS's native grey background");
            check(outside == 0 && narrowOutside == 0 && mismatches(narrowExpected, narrowZero) == 0,
                    "The rail rendering escaped its actual bounds or narrow zero-opacity shape");
            Files.writeString(output.resolve("result.txt"), "PASS: real BNS rail21x64; exact custom artwork at0; background-only opacity; four unchanged native13x13 buttons; native non-Nexus scope; narrow rail bounds; loaded BNS merged highlight redirect adds no second Nexus tint and preserves native white elsewhere.\n");
        } finally {
            minecraft.screen = oldScreen;
            AstralClientConfig.astralInterfaceOverlayOpacity.set(oldOpacity);
            AstralPlaneRenderType.ASTRAL_OVERLAY.setupRenderState(); AstralPlaneRenderType.ASTRAL_OVERLAY.clearRenderState();
            images.values().forEach(NativeImage::close);
            if (target != null) target.destroyBuffers();
            RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix();
            texture.restoreLastBlurMipmap(); gl.restore();
        }
    }

    private static String verifyHighlights(Capture capture, Class<?> overlay, NexusScreen nexus, Screen nonNexus) throws Exception {
        // Invoke the handler merged into the loaded optional target, not the mixin class. Loading
        // that target also requires its redirect to match the real renderEntries call site.
        // This bounded GPU check leaves live BNS inventory, identity windows, and search untouched.
        Method redirect = java.util.Arrays.stream(overlay.getDeclaredMethods())
                .filter(candidate -> candidate.getName().contains("astral$keepSingleNexusHighlight"))
                .filter(candidate -> java.lang.reflect.Modifier.isStatic(candidate.getModifiers()))
                .filter(candidate -> java.util.Arrays.equals(candidate.getParameterTypes(),
                        new Class<?>[] {GuiGraphics.class, int.class, int.class, int.class}))
                .findFirst().orElseThrow(() -> new IllegalStateException("BNS highlight redirect was not merged into the loaded overlay"));
        redirect.setAccessible(true);
        Method tint = method(NexusScreen.class, "translucentSprite", GuiGraphics.class, int.class, int.class,
                int.class, int.class, int.class, int.class);
        NativeImage nexusBaseline = capture.drawHighlight("nexus_slot_tint_baseline", nexus, redirect, tint, 0);
        NativeImage nexusActual = capture.drawHighlight("nexus_slot_bns_redirect", nexus, redirect, tint, 1);
        NativeImage nativePlain = capture.drawHighlight("native_slot_plain", nonNexus, redirect, tint, 0);
        NativeImage nativeActual = capture.drawHighlight("native_slot_bns_redirect", nonNexus, redirect, tint, 1);
        NativeImage nativeExpected = capture.drawHighlight("native_slot_vanilla_highlight", nonNexus, redirect, tint, 2);
        int secondTint = mismatches(nexusBaseline, nexusActual), nativeChanges = mismatches(nativePlain, nativeActual);
        int nativeMismatch = mismatches(nativeExpected, nativeActual), firstTint = mismatches(nativePlain, nexusBaseline);
        String report = "highlight_boundary=direct loaded BNS merged redirect; populated PAPER GPU slot; no inventory/search mutation\n"
                + "highlight_redirect=" + redirect.getName() + "\nnexus_extra_highlight_pixels=" + secondTint
                + "\nnexus_primary_tint_pixels=" + firstTint + "\nnative_highlight_changed_pixels=" + nativeChanges
                + "\nnative_highlight_reference_mismatches=" + nativeMismatch + "\n";
        Files.writeString(capture.output.resolve("highlight_metrics.txt"), report);
        check(firstTint > 40, "The actual Nexus translucent tint helper did not render behind the item");
        check(secondTint == 0, "BNS added another highlight over the Nexus tint: " + secondTint);
        check(nativeChanges > 40 && nativeMismatch == 0, "BNS non-Nexus highlight no longer matches vanilla: changes=" + nativeChanges + ", mismatches=" + nativeMismatch);
        return report;
    }

    private record Bounds(int left, int top, int width, int height) {}
    private static Bounds bounds(Object rail) throws Exception {
        int left = coordinate(rail, "left"), top = coordinate(rail, "top");
        return new Bounds(left, top, coordinate(rail, "right") - left, coordinate(rail, "bottom") - top);
    }
    private static int coordinate(Object object, String name) throws Exception { return (int)method(object.getClass(), name).invoke(object); }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception { Method method = type.getDeclaredMethod(name, arguments); method.setAccessible(true); return method; }
    private static String version(String id) { return ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion().toString(); }

    private record Capture(TextureTarget target, GuiGraphics graphics, Method background, Method toolbar, Object rail, Bounds bounds, Path output, Map<String, NativeImage> images) {
        NativeImage drawHighlight(String name, Screen screen, Method redirect, Method tint, int mode) throws Exception {
            Minecraft.getInstance().screen = screen;
            target.setClearColor(.02F, .03F, .05F, 1); RenderSystem.depthMask(true); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderGameTime(4200, 0); RenderSystem.setShaderColor(1, 1, 1, 1);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems(); RenderSystem.disableDepthTest();
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            // The exact native-inventory well and production tint precede the BNS draw boundary.
            graphics.blit(CONTROLS, X - 1, Y - 1, 7, 179, 18, 18, 318, 266);
            if (screen instanceof NexusScreen) tint.invoke(null, graphics, X, Y, 0, 0, 16, 16);
            if (mode == 1) redirect.invoke(null, graphics, X, Y, 0);
            else if (mode == 2) AbstractContainerScreen.renderSlotHighlight(graphics, X, Y, 0);
            graphics.renderItem(new ItemStack(Items.PAPER), X, Y); graphics.flush();
            NativeImage image = Screenshot.takeScreenshot(target); images.put(name, image); image.writeToFile(output.resolve(name + ".png")); return image;
        }
        NativeImage draw(String name, Screen screen, double opacity, boolean expected, boolean buttons) throws Exception {
            Minecraft.getInstance().screen = screen; AstralClientConfig.astralInterfaceOverlayOpacity.set(opacity);
            target.setClearColor(.02F, .03F, .05F, 1); RenderSystem.depthMask(true); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderGameTime(4200, 0); com.mojang.blaze3d.platform.Lighting.setupFor3DItems(); RenderSystem.disableDepthTest(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(X - bounds.left, Y - bounds.top, 0);
                if (expected) graphics.blit(TEXTURE, bounds.left, bounds.top, 0, 0, bounds.width, bounds.height, bounds.width, bounds.height);
                else if (buttons) toolbar.invoke(null, graphics, -10000, -10000);
                else background.invoke(rail, graphics);
                graphics.flush();
            } finally { graphics.pose().popPose(); }
            NativeImage image = Screenshot.takeScreenshot(target); images.put(name, image); image.writeToFile(output.resolve(name + ".png")); return image;
        }
    }
    private static int rgb(int rgba) { return (rgba & 255) << 16 | (rgba >>> 8 & 255) << 8 | (rgba >>> 16 & 255); }
    private static int mismatches(NativeImage first, NativeImage second) {
        int changed = 0; for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (first.getPixelRGBA(x, y) != second.getPixelRGBA(x, y)) changed++; return changed;
    }
    private static int outsideChanges(NativeImage first, NativeImage second, int width, int height) {
        int changed = 0; for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if ((x < X || y < Y || x >= X + width || y >= Y + height) && first.getPixelRGBA(x, y) != second.getPixelRGBA(x, y)) changed++; return changed;
    }
    private static double difference(NativeImage first, NativeImage second, int width, int height) {
        double sum = 0; for (int y = Y; y < Y + height; y++) for (int x = X; x < X + width; x++) {
            int a = first.getPixelRGBA(x, y), b = second.getPixelRGBA(x, y);
            sum += Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255)) + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255));
        } return sum / (width * height * 3.0 * 255);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    /** Captures the GL state touched by GUI buffers, item icons, the offscreen target, and the material. */
    private static final class GlSnapshot {
        private final net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final VertexSorting sorting = RenderSystem.getVertexSorting();
        private final float time = RenderSystem.getShaderGameTime(), fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        private final float[] color = RenderSystem.getShaderColor().clone(), clearColor = new float[4];
        private final int[] viewport = new int[4], scissorBox = new int[4], colorMask = new int[4], textures = new int[3], bindings = new int[3];
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final Vector3f[] lights;
        GlSnapshot() throws Exception {
            Vector3f[] currentLights = (Vector3f[])field(RenderSystem.class, "shaderLightDirections").get(null);
            lights = new Vector3f[] { new Vector3f(currentLights[0]), new Vector3f(currentLights[1]) };
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox); GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);
            for (int unit = 0; unit < 3; unit++) { textures[unit] = RenderSystem.getShaderTexture(unit); RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit); bindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
            RenderSystem.activeTexture(activeTexture);
        }
        void restore() {
            RenderSystem.setShaderLights(lights[0], lights[1]); RenderSystem.setProjectionMatrix(projection, sorting); RenderSystem.setShaderGameTime(0, time * 24000);
            RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]); RenderSystem.setShader(() -> shader);
            for (int unit = 0; unit < 3; unit++) { RenderSystem.setShaderTexture(unit, textures[unit]); RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit); RenderSystem.bindTexture(bindings[unit]); }
            RenderSystem.activeTexture(activeTexture); RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(depthMask);
            RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0); RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull(); if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend(); if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]); else RenderSystem.disableScissor();
            BufferUploader.reset(); GL30.glBindVertexArray(vao); GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer); GlStateManager._glUseProgram(program);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer); GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}
