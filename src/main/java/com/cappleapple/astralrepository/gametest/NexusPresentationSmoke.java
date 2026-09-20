package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.NexusScreen;
import com.cappleapple.astralrepository.menu.NexusMenu;
import com.cappleapple.astralrepository.network.NetworkPackets;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

/** Excluded client regression fixture for Nexus paint order, conditional panels, and scrollbar input. */
public final class NexusPresentationSmoke {
    private static final int SIZE = 384, X = 24, Y = 24;
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID, "textures/gui/nexus.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID, "textures/gui/nexus_widgets.png");
    private NexusPresentationSmoke() {}

    public static void verify(NexusScreen screen) throws Exception {
        check(Boolean.getBoolean("astral_repository.clientSmoke"), "Presentation fixture requires the development smoke client");
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        check(minecraft.screen == screen, "Presentation fixture requires the current Nexus screen");
        NexusMenu menu = screen.getMenu();
        var entries = menu.entries; var jobs = menu.jobs; int total = menu.totalEntries, menuRow = menu.scrollRow; String error = menu.error;
        Map<Field, Object> fields = new LinkedHashMap<>();
        for (Field field : NexusScreen.class.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
            field.setAccessible(true); fields.put(field, field.get(screen));
        }
        var oldFocused = screen.getFocused();
        Map<net.minecraft.client.gui.components.AbstractWidget, Boolean> focus = new LinkedHashMap<>();
        for (var child : screen.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) focus.put(widget, widget.isFocused());
        Method controls = method(NexusScreen.class, "updateControls"), render = method(NexusScreen.class, "renderBg", GuiGraphics.class, float.class, int.class, int.class);
        double oldOpacity = AstralClientConfig.astralInterfaceOverlayOpacity.get();
        GlSnapshot gl = new GlSnapshot();
        TextureTarget target = null;
        Map<String, NativeImage> images = new LinkedHashMap<>();
        Path output = Path.of("../build/client-smoke/nexus-presentation"); Files.createDirectories(output);
        RenderSystem.getModelViewStack().pushMatrix();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(8192); NativeImage raw = NativeImage.read(minecraft.getResourceManager().open(PANEL));
             NativeImage atlas = NativeImage.read(minecraft.getResourceManager().open(WIDGETS))) {
            AstralClientConfig.astralInterfaceOverlayOpacity.set(0.0);
            RenderSystem.disableScissor(); RenderSystem.depthMask(true); RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableCull(); RenderSystem.setShaderColor(1, 1, 1, 1);
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            GuiGraphics graphics = new GuiGraphics(minecraft, MultiBufferSource.immediate(memory));
            Capture capture = new Capture(target, graphics, screen, render, controls, output, images);
            menu.entries = List.of(); menu.jobs = List.of(); menu.totalEntries = 0; menu.scrollRow = 0; menu.error = "";
            field(NexusScreen.class, "selected").set(screen, ItemStack.EMPTY); field(NexusScreen.class, "row").setInt(screen, 0); field(NexusScreen.class, "jobOffset").setInt(screen, 0);
            NativeImage empty = capture.draw("empty", -10000, -10000, -1);
            NativeImage emptyHover = capture.draw("empty_hover", screen.getGuiLeft() + 18, screen.getGuiTop() + 57, -1);
            var nativeSlot = menu.slots.subList(1, 10).stream().filter(slot -> slot.getItem().isEmpty()).findFirst().orElseThrow();
            int nativeMouseX = screen.getGuiLeft() + nativeSlot.x + 8, nativeMouseY = screen.getGuiTop() + nativeSlot.y + 8;
            NativeImage nativeHover = capture.draw("native_crafting_hover", nativeMouseX, nativeMouseY, -1);
            NativeImage nativeAfterHighlight = capture.draw("native_crafting_after_highlight", nativeMouseX, nativeMouseY, -1, nativeSlot);
            List<NetworkPackets.Job> terminal = List.of(job(NetworkPackets.JobState.COMPLETE), job(NetworkPackets.JobState.FAILED), job(NetworkPackets.JobState.CANCELLED),
                    new NetworkPackets.Job(UUID.randomUUID(), ItemStack.EMPTY, 0, NetworkPackets.JobState.WAITING, 0, 1, 0, ""));
            menu.jobs = terminal;
            NativeImage terminalOnly = capture.draw("terminal_only", -10000, -10000, -1);
            var running = job(NetworkPackets.JobState.RUNNING); var waiting = job(NetworkPackets.JobState.WAITING); var external = job(NetworkPackets.JobState.EXTERNAL);
            menu.jobs = List.of(terminal.getFirst(), running, terminal.get(1));
            NativeImage one = capture.draw("one_current_job", -10000, -10000, -1);
            menu.jobs = List.of(terminal.getFirst(), running, terminal.get(2), waiting);
            NativeImage two = capture.draw("two_current_jobs", -10000, -10000, -1);
            menu.jobs = List.of(running, waiting, external, job(NetworkPackets.JobState.RUNNING));
            NativeImage four = capture.draw("four_current_jobs", -10000, -10000, -1);
            menu.jobs = List.of();
            menu.totalEntries = 54;
            NativeImage fits = capture.draw("items_54_no_scrollbar", -10000, -10000, -1);
            menu.totalEntries = 55;
            int height55 = screen.storageThumbHeight();
            NativeImage overflow = capture.draw("items_55_scrollbar", -10000, -10000, -1);
            menu.totalEntries = 108; int height108 = screen.storageThumbHeight();
            capture.draw("items_108_scrollbar", -10000, -10000, -1);
            menu.totalEntries = 900; int height900 = screen.storageThumbHeight();
            capture.draw("items_900_scrollbar", -10000, -10000, -1);
            menu.totalEntries = 108; field(NexusScreen.class, "row").setInt(screen, 3);
            int thumbTop = screen.storageThumbTop(); double grab = 9, mouseX = screen.getGuiLeft() + 194, mouseY = screen.getGuiTop() + thumbTop + grab;
            check(screen.mouseClicked(mouseX, mouseY, 0), "Actual thumb press was not accepted");
            int afterPress = screen.scrollRow();
            check(screen.mouseDragged(mouseX, mouseY, 0, 0, 0), "Actual held-thumb drag was not accepted");
            int afterStationaryDrag = screen.scrollRow();
            check(screen.mouseReleased(mouseX, mouseY, 0), "Actual held-thumb release was not accepted");
            int first = screen.storageRowAt(screen.getGuiTop() + 49 + grab, grab);
            int last = screen.storageRowAt(screen.getGuiTop() + 49 + 108 - screen.storageThumbHeight() + grab, grab);
            menu.totalEntries = 9000; field(NexusScreen.class, "row").setInt(screen, 333);
            double denseY = screen.getGuiTop() + screen.storageThumbTop() + 7;
            check(screen.mouseClicked(mouseX, denseY, 0), "Dense thumb press was not accepted");
            screen.mouseDragged(mouseX, denseY, 0, 0, 0); int denseRow = screen.scrollRow();
            screen.mouseReleased(mouseX, denseY, 0);
            menu.totalEntries = 55; field(NexusScreen.class, "row").setInt(screen, 0);
            double collapsingY = screen.getGuiTop() + screen.storageThumbTop() + 4;
            check(screen.mouseClicked(mouseX, collapsingY, 0), "Collapsing thumb press was not accepted");
            menu.totalEntries = 54; controls.invoke(screen);
            boolean collapsingRelease = screen.mouseReleased(mouseX, collapsingY, 0);
            boolean draggingAfterRelease = field(NexusScreen.class, "draggingStorage").getBoolean(screen);
            // The same production mapping handles the ends; only a stationary actual drag is dispatched,
            // so synthetic entry counts never generate SEARCH packets or mutate the integrated server.
            menu.totalEntries = 1; field(NexusScreen.class, "row").setInt(screen, 0);
            ItemStack item = new ItemStack(Items.PAPER);
            menu.entries = List.of(new NexusMenu.Entry(item, 0, false));
            NativeImage itemPlain = capture.draw("item_plain", -10000, -10000, -1);
            NativeImage itemHover = capture.draw("item_hover", screen.getGuiLeft() + 18, screen.getGuiTop() + 57, -1);
            field(NexusScreen.class, "selected").set(screen, item.copy());
            NativeImage itemSelected = capture.draw("item_selected", -10000, -10000, -1);
            NativeImage itemSelectedHover = capture.draw("item_selected_hover", screen.getGuiLeft() + 18, screen.getGuiTop() + 57, -1);
            NativeImage blackMask = capture.draw("item_mask_black", -10000, -10000, 0);
            NativeImage whiteMask = capture.draw("item_mask_white", -10000, -10000, 1);
            int opaque = 0, hoverItemChanges = 0, selectedItemChanges = 0;
            for (int y = Y + 50; y < Y + 66; y++) for (int x = X + 11; x < X + 27; x++) {
                if (blackMask.getPixelRGBA(x, y) == whiteMask.getPixelRGBA(x, y)) {
                    opaque++;
                    if (itemPlain.getPixelRGBA(x, y) != itemHover.getPixelRGBA(x, y)) hoverItemChanges++;
                    if (itemPlain.getPixelRGBA(x, y) != itemSelected.getPixelRGBA(x, y)) selectedItemChanges++;
                }
            }
            int hoverAlphaErrors = 0, hoverBlendErrors = 0, hoverMaxError = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int sourcePixel = atlas.getPixelRGBA(x, y), px = X + 11 + x, py = Y + 50 + y;
                if ((sourcePixel >>> 24) != 48) hoverAlphaErrors++;
                int distance = channelError(composite(empty.getPixelRGBA(px, py), sourcePixel), emptyHover.getPixelRGBA(px, py));
                hoverMaxError = Math.max(hoverMaxError, distance); if (distance > 1) hoverBlendErrors++;
            }
            int nativeBlendErrors = 0, nativeMaxError = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int px = X + nativeSlot.x + x, py = Y + nativeSlot.y + y;
                int distance = channelError(composite(empty.getPixelRGBA(px, py), atlas.getPixelRGBA(x, y)), nativeHover.getPixelRGBA(px, py));
                nativeMaxError = Math.max(nativeMaxError, distance); if (distance > 1) nativeBlendErrors++;
            }
            int nativeSecondOverlay = changed(nativeHover, nativeAfterHighlight, 0, 0, 318, 266);
            int selectionAlphaErrors = 0, selectionBlendErrors = 0, combinedBlendErrors = 0, selectionMaxError = 0;
            int outerSamples = 0, innerSamples = 0;
            for (int y = 0; y < 18; y++) for (int x = 0; x < 18; x++) {
                int sourcePixel = atlas.getPixelRGBA(18 + x, y), px = X + 10 + x, py = Y + 49 + y;
                if ((sourcePixel >>> 24) != 0 && (sourcePixel >>> 24) != 96) selectionAlphaErrors++;
                // Black/white reference renders identify completely transparent parts of the real
                // item, so the predicted backdrop excludes any item-alpha composition of its own.
                if ((blackMask.getPixelRGBA(px, py) & 0xffffff) != 0 || (whiteMask.getPixelRGBA(px, py) & 0xffffff) != 0xffffff) continue;
                int backdrop = itemPlain.getPixelRGBA(px, py);
                int selectionExpected = composite(backdrop, sourcePixel);
                int hoverSource = x >= 1 && x <= 16 && y >= 1 && y <= 16 ? atlas.getPixelRGBA(x - 1, y - 1) : 0;
                int combinedExpected = composite(composite(backdrop, hoverSource), sourcePixel);
                int distance = channelError(selectionExpected, itemSelected.getPixelRGBA(px, py));
                selectionMaxError = Math.max(selectionMaxError, distance); if (distance > 1) selectionBlendErrors++;
                if (channelError(combinedExpected, itemSelectedHover.getPixelRGBA(px, py)) > 1) combinedBlendErrors++;
                if ((sourcePixel >>> 24) != 0) {
                    if (x == 0 || y == 0 || x == 17 || y == 17) outerSamples++; else innerSamples++;
                }
            }
            int emptyJobs = rawMismatches(raw, empty, 207, 168, 108, 86);
            int placeholdersOne = changed(empty, one, 207, 205, 97, 46), placeholdersTwo = changed(empty, two, 207, 229, 97, 22);
            int noScrollbar = rawMismatches(raw, fits, 191, 49, 7, 108), emptyScrollbar = rawMismatches(raw, empty, 191, 49, 7, 108);
            String report = "Actual Nexus renderBg and vanilla item renderer;384x384GPU FBO; GUIopacity0 for exact artwork checks.\n"
                    + "empty_jobs_panel_mismatches=" + emptyJobs + "\nterminal_only_changed_pixels=" + changed(empty, terminalOnly, 0, 0, 318, 266)
                    + "\none_job_unused_rows=" + placeholdersOne + "\ntwo_jobs_unused_row=" + placeholdersTwo
                    + "\none_job_visible_pixels=" + changed(empty, one, 207, 181, 97, 22) + "\ntwo_jobs_second_row_pixels=" + changed(empty, two, 207, 205, 97, 22)
                    + "\none_job_scrollbar_pixels=" + changed(empty, one, 307, 181, 6, 70) + "\nfour_job_scrollbar_pixels=" + changed(empty, four, 307, 181, 6, 70)
                    + "\nempty_item_scrollbar_mismatches=" + emptyScrollbar + "\n54_item_scrollbar_mismatches=" + noScrollbar
                    + "\n55_item_scrollbar_changed_pixels=" + changed(empty, overflow, 191, 49, 7, 108)
                    + "\nthumb_heights_55_108_900=" + height55 + "," + height108 + "," + height900 + "\nrow_before_press=3\nrow_after_press=" + afterPress
                    + "\nrow_after_stationary_drag=" + afterStationaryDrag + "\ndrag_mapping_first_last=" + first + "," + last + "\ndense_stationary_row=" + denseRow + "\noverflow_disappeared_release_consumed=" + collapsingRelease + "\ndragging_after_release=" + draggingAfterRelease
                    + "\nopaque_item_pixels=" + opaque + "\nhover_changed_opaque_item_pixels=" + hoverItemChanges + "\nselection_changed_opaque_item_pixels=" + selectedItemChanges
                    + "\nhover_atlas_alpha_errors=" + hoverAlphaErrors + "\nhover_blend_pixel_errors=" + hoverBlendErrors + "\nhover_blend_max_channel_error=" + hoverMaxError
                    + "\nnative_hover_blend_pixel_errors=" + nativeBlendErrors + "\nnative_hover_blend_max_channel_error=" + nativeMaxError + "\nnative_second_overlay_changed_pixels=" + nativeSecondOverlay
                    + "\nselection_atlas_alpha_errors=" + selectionAlphaErrors + "\nselection_blend_pixel_errors=" + selectionBlendErrors + "\nselection_blend_max_channel_error=" + selectionMaxError
                    + "\nselection_outer_inner_samples=" + outerSamples + "," + innerSamples + "\ncombined_highlight_blend_pixel_errors=" + combinedBlendErrors
                    + "\nhover_changed_slot_pixels=" + changed(itemPlain, itemHover, 10, 49, 18, 18) + "\nselection_changed_slot_pixels=" + changed(itemPlain, itemSelected, 10, 49, 18, 18) + "\n";
            Files.writeString(output.resolve("metrics.txt"), report); AstralRepository.LOGGER.info("Nexus presentation measurements before assertions:\n{}", report);
            check(emptyJobs == 0 && changed(empty, terminalOnly, 0, 0, 318, 266) == 0, "Empty or terminal jobs left a visible jobs area");
            check(placeholdersOne == 0 && placeholdersTwo == 0, "Unused job rows still render placeholders");
            check(changed(empty, one, 207, 181, 97, 22) > 100 && changed(empty, two, 207, 205, 97, 22) > 100, "Current jobs did not render into consecutive rows");
            check(changed(empty, one, 307, 181, 6, 70) == 0 && changed(empty, four, 307, 181, 6, 70) > 50, "Job scrollbar did not follow actual overflow");
            check(emptyScrollbar == 0 && noScrollbar == 0 && changed(empty, overflow, 191, 49, 7, 108) > 100, "Item scrollbar did not appear only for overflow");
            check(height55 == 93 && height108 == 54 && height900 == 16, "Item thumb is not proportional to six visible rows with16px minimum");
            check(afterPress == 3 && afterStationaryDrag == 3 && first == 0 && last == 6, "Scrollbar grab offset jumped or prevented reaching an end");
            check(denseRow == 333 && collapsingRelease && !draggingAfterRelease, "Quantized drag jumped or disappearing scrollbar released input to vanilla");
            check(hoverAlphaErrors == 0 && selectionAlphaErrors == 0, "Highlight atlas must use hover48/255 and selection96/255 alpha");
            check(nativeBlendErrors == 0 && nativeSecondOverlay == 0, "Native crafting hover is not translucent or receives a second after-item overlay");
            check(hoverBlendErrors == 0 && selectionBlendErrors == 0 && combinedBlendErrors == 0 && outerSamples >= 60 && innerSamples > 0,
                    "Actual highlight pixels do not match source-alpha composition within one byte");
            check(opaque > 30 && hoverItemChanges == 0 && selectedItemChanges == 0, "Hover or selection paints over opaque item pixels");
            check(changed(itemPlain, itemHover, 10, 49, 18, 18) > 10 && changed(itemPlain, itemSelected, 10, 49, 18, 18) > 10, "Behind-item highlights are missing");
            Files.writeString(output.resolve("result.txt"), "PASS: hover48/255 and outline96/255 match source-alpha composition within one byte; highlights behind opaque item pixels; current jobs only, no empty area/placeholders; overflow-only proportional scrollbar; preserved drag grab and reachable ends.\n");
        } finally {
            menu.entries = entries; menu.jobs = jobs; menu.totalEntries = total; menu.scrollRow = menuRow; menu.error = error;
            for (var entry : fields.entrySet()) entry.getKey().set(screen, entry.getValue());
            screen.setFocused(oldFocused); focus.forEach((widget, focused) -> widget.setFocused(focused)); controls.invoke(screen);
            AstralClientConfig.astralInterfaceOverlayOpacity.set(oldOpacity);
            images.values().forEach(NativeImage::close); if (target != null) target.destroyBuffers();
            RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix(); gl.restore();
        }
    }

    private static NetworkPackets.Job job(NetworkPackets.JobState state) {
        return new NetworkPackets.Job(UUID.randomUUID(), new ItemStack(Items.IRON_INGOT), 16, state, 2, 4, state == NetworkPackets.JobState.RUNNING ? 1 : 0, "");
    }
    private record Capture(TextureTarget target, GuiGraphics graphics, NexusScreen screen, Method render, Method controls, Path output, Map<String, NativeImage> images) {
        NativeImage draw(String name, int mouseX, int mouseY, int mask) throws Exception { return draw(name, mouseX, mouseY, mask, null); }
        NativeImage draw(String name, int mouseX, int mouseY, int mask, net.minecraft.world.inventory.Slot afterHighlight) throws Exception {
            target.setClearColor(mask == 1 ? 1 : 0, mask == 1 ? 1 : 0, mask == 1 ? 1 : 0, 1);
            RenderSystem.depthMask(true); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderGameTime(4200, 0); Lighting.setupFor3DItems(); RenderSystem.disableDepthTest(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(X - screen.getGuiLeft(), Y - screen.getGuiTop(), 0);
                if (mask >= 0) graphics.renderItem(new ItemStack(Items.PAPER), screen.getGuiLeft() + 11, screen.getGuiTop() + 50);
                else {
                    controls.invoke(screen); render.invoke(screen, graphics, 0F, mouseX, mouseY);
                    if (afterHighlight != null) {
                        graphics.pose().pushPose();
                        try {
                            graphics.pose().translate(screen.getGuiLeft(), screen.getGuiTop(), 0);
                            method(NexusScreen.class, "renderSlotHighlight", GuiGraphics.class, net.minecraft.world.inventory.Slot.class, int.class, int.class, float.class)
                                    .invoke(screen, graphics, afterHighlight, mouseX, mouseY, 0F);
                        } finally { graphics.pose().popPose(); }
                    }
                }
                graphics.flush();
            } finally { graphics.pose().popPose(); }
            NativeImage image = Screenshot.takeScreenshot(target); images.put(name, image); image.writeToFile(output.resolve(name + ".png")); return image;
        }
    }
    private static int rawMismatches(NativeImage raw, NativeImage rendered, int left, int top, int width, int height) {
        int changed = 0; for (int y = top; y < top + height; y++) for (int x = left; x < left + width; x++) if (raw.getPixelRGBA(x, y) != rendered.getPixelRGBA(X + x, Y + y)) changed++; return changed;
    }
    private static int changed(NativeImage first, NativeImage second, int left, int top, int width, int height) {
        int changed = 0; for (int y = Y + top; y < Y + top + height; y++) for (int x = X + left; x < X + left + width; x++) if (first.getPixelRGBA(x, y) != second.getPixelRGBA(x, y)) changed++; return changed;
    }
    private static int composite(int backdrop, int foreground) {
        int alpha = foreground >>> 24, result = 0xff000000;
        for (int shift = 0; shift < 24; shift += 8) result |= (int)Math.round(((foreground >>> shift & 255) * alpha + (backdrop >>> shift & 255) * (255 - alpha)) / 255.0) << shift;
        return result;
    }
    private static int channelError(int expected, int actual) {
        int error = 0; for (int shift = 0; shift < 24; shift += 8) error = Math.max(error, Math.abs((expected >>> shift & 255) - (actual >>> shift & 255))); return error;
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception { Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method; }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

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
