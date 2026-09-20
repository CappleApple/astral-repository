package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralClientConfig;
import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.client.CrystalModelRenderer;
import com.cappleapple.astralrepository.content.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

/** Dedicated excluded client profile: real node models, world gallery, GUI items, and material masks. */
public final class NodeModelSmoke {
    private static final Path OUT = Path.of("../build/client-smoke/node-models");
    private static final int VARIANTS=8;
    private static final int SIZE = 512, CELL = 128, MAX_TICKS = 1800;
    private static int stage, ticks;
    private static CompletableFuture<Void> pending;
    private static double originalOpacity = Double.NaN;
    private static boolean originalHideGui;
    private static final Set<String> SPRITES = Set.of("block/node_crystal", "block/node_stone", "block/node_trim", "block/node_channel");
    private NodeModelSmoke() {}

    /** Called only by the dedicated -PnodeModels client branch; returns true after the final capture. */
    public static boolean tick() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        check(Boolean.getBoolean("astral_repository.nodeModels") && Boolean.getBoolean("astral_repository.clientSmoke"), "Node fixture requires its development profile");
        if (++ticks > MAX_TICKS) throw new IllegalStateException("Node model showcase timed out in stage " + stage);
        try {
            if (pending != null) { if (!pending.isDone()) return false; pending.join(); pending = null; ticks = 0; }
            switch (stage) {
                case 0 -> {
                    ParallaxMaterialSmoke.verify();
                    Files.createDirectories(OUT); originalOpacity = AstralClientConfig.astralOverlayOpacity.get(); originalHideGui = mc.options.hideGui;
                    mc.options.hideGui = true; pending = server(NodeModelSmoke::placeGallery); advance();
                }
                case 1 -> {
                    if (ticks < 60 || !galleryLoaded()) return false;
                    verifyGeometry(); verifyGpu(); screenshot("world_front_default.png");
                    AstralClientConfig.astralOverlayOpacity.set(0.0); advance();
                }
                case 2 -> { if (ticks < 20) return false; screenshot("world_front_zero.png"); AstralClientConfig.astralOverlayOpacity.set(1.0); advance(); }
                case 3 -> { if (ticks < 20) return false; screenshot("world_front_full.png"); AstralClientConfig.astralOverlayOpacity.set(originalOpacity); pending = server(p -> look(p, 44.5, -52, -10.5, new Vec3(44.5, -58.5, 4.5))); advance(); }
                case 4 -> { if (ticks < 50 || !galleryLoaded()) return false; screenshot("world_rear_default.png");pending=server(p->{for(int i=0;i<VARIANTS;i++)p.serverLevel().setBlockAndUpdate(position(i),p.serverLevel().getBlockState(position(i)).setValue(CrystalNodeBlock.FACING,Direction.values()[i%6]));});stage=40;ticks=0; }
                case 40 -> {if(ticks<30)return false;screenshot("world_oriented.png");mc.setScreen(new Gallery());stage=5;ticks=0;}
                case 5 -> { if (ticks < 15) return false; screenshot("items_front_default.png"); ((Gallery)mc.screen).yaw = 180; advance(); }
                case 6 -> {
                    if (ticks < 15) return false; screenshot("items_rear_default.png"); restore(); mc.setScreen(null);
                    check(!mc.mouseHandler.isMouseGrabbed(), "Model client grabbed the mouse");
                    check(mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0, "Model client was not muted");
                    Files.writeString(OUT.resolve("result.txt"), "PASS:4 registered node blocks and 4 upgrade variants in world and GUI; bounded geometry/custom sprites/six facing states; front/rear world and item captures; actual crystal material masks isolate opacity changes from stone and trim; culling active.\n");
                    return true;
                }
            }
            return false;
        } catch (Throwable failure) {
            restore(); Files.createDirectories(OUT); Files.writeString(OUT.resolve("result.txt"), "FAIL: " + failure + "\n"); throw failure;
        }
    }
    private static void advance() { stage++; ticks = 0; }
    private static void restore() { if (!Double.isNaN(originalOpacity)) AstralClientConfig.astralOverlayOpacity.set(originalOpacity); Minecraft.getInstance().options.hideGui = originalHideGui; }
    private static CrystalNodeBlock block(int i){return i<4?AstralContent.NODES.get(i).get():i<6?AstralContent.SEED_STORAGE_CRYSTAL.get():AstralContent.RELAY_CRYSTAL.get();}
    private static String name(int i){return i<4?BuiltInRegistries.BLOCK.getKey(block(i)).getPath():java.util.List.of("moon_storage_crystal","star_storage_crystal","remote_crystal","gateway_crystal").get(i-4);}
    private static ItemStack stack(int i){var stack=new ItemStack(block(i));if(i>=4){var mc=Minecraft.getInstance();var n=(CrystalNodeBlockEntity)mc.level.getBlockEntity(position(i));stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(n.saveWithFullMetadata(mc.level.registryAccess())));}return stack;}
    private static BlockPos position(int index) { return new BlockPos(40 + index % 4 * 3, -59, index / 4 * 4); }
    private static void placeGallery(ServerPlayer player) {
        var level = player.serverLevel(); level.setDayTime(6000); level.setWeatherParameters(12000, 0, false, false);
        player.closeContainer(); player.getAbilities().flying = true; player.onUpdateAbilities();
        for (int z = -2; z <= 11; z++) for (int x = 38; x <= 52; x++) level.setBlockAndUpdate(new BlockPos(x, -60, z), Blocks.SMOOTH_STONE.defaultBlockState());
        for(int i=0;i<VARIANTS;i++){
            level.setBlockAndUpdate(position(i),block(i).defaultBlockState());var n=(CrystalNodeBlockEntity)level.getBlockEntity(position(i));
            if(i==4||i==5){n.upgradeStorage(2);if(i==5)n.upgradeStorage(3);}
            if(i>=6){n.upgradeRange();if(i==7)n.setDimensional(true);}
        }
        look(player, 44.5, -52, 18.5, new Vec3(44.5, -58.5, 4.5));
    }
    private static boolean galleryLoaded() {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        for (int i = 0; i < VARIANTS; i++) if (!level.getBlockState(position(i)).is(block(i)) || !(level.getBlockEntity(position(i)) instanceof CrystalNodeBlockEntity)) return false;
        return true;
    }
    private static void look(ServerPlayer player, double x, double y, double z, Vec3 target) {
        Vec3 delta = target.subtract(x, y + player.getEyeHeight(), z);
        float yaw = (float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90, pitch = (float)-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        player.connection.teleport(x, y, z, yaw, pitch);
    }
    private static CompletableFuture<Void> server(Consumer<ServerPlayer> action) {
        var client = Minecraft.getInstance(); var result = new CompletableFuture<Void>(); var id = client.player.getUUID();
        client.getSingleplayerServer().execute(() -> { try { action.accept(client.getSingleplayerServer().getPlayerList().getPlayer(id)); result.complete(null); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        return result;
    }
    private static void screenshot(String file) throws Exception { try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(OUT.resolve(file)); } }

    private static void verifyGeometry() throws Exception {
        var mc = Minecraft.getInstance(); check(AstralContent.NODES.size() == 4, "Showcase expects all four registered network blocks, including the Nexus");
        StringBuilder report = new StringBuilder("Registry-backed node models; repeated fourth vertex is allowed for OBJ triangles.\n");
        for (var entry : AstralContent.NODES) {
            var block = entry.get(); var id = BuiltInRegistries.BLOCK.getKey(block);
            BakedModel item = mc.getItemRenderer().getModel(new ItemStack(block), mc.level, mc.player, 0);
            check(item.isCustomRenderer(), id + " item does not dispatch the node renderer");
            BakedModel underlying = mc.getModelManager().getModel(CrystalModelRenderer.modelLocation(block));
            int itemFaces = inspect(underlying, null, id);
            check(!underlying.isCustomRenderer(), id + " underlying item geometry recursively dispatches a custom renderer");
            int worldFaces = 0;
            for (Direction facing : Direction.values()) {
                BlockState state = block.defaultBlockState().setValue(CrystalNodeBlock.FACING, facing);
                check(state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED, id + " still has a duplicate chunk-render pass");
                worldFaces = inspect(mc.getBlockRenderer().getBlockModel(state), state, id);
            }
            report.append(id).append(" item_faces=").append(itemFaces).append(" world_faces=").append(worldFaces).append(" facing_states=6\n");
        }
        for(int i=4;i<VARIANTS;i++){var id=new ResourceLocation("astral_repository",name(i));int faces=inspect(mc.getModelManager().getModel(CrystalModelRenderer.modelLocation(name(i))),null,id);report.append(id).append(" standalone_upgrade_faces=").append(faces).append('\n');}
        Files.writeString(OUT.resolve("geometry.txt"), report); AstralRepository.LOGGER.info("Node model geometry:\n{}", report);
    }
    private static int inspect(BakedModel model, BlockState state, ResourceLocation id) {
        check(model != Minecraft.getInstance().getModelManager().getMissingModel(), id + " uses the missing model");
        List<BakedQuad> quads = new ArrayList<>(); var random = RandomSource.create(42);
        for (Direction direction : Direction.values()) { random.setSeed(42); quads.addAll(model.getQuads(state, direction, random, ModelData.EMPTY, null)); }
        random.setSeed(42); quads.addAll(model.getQuads(state, null, random, ModelData.EMPTY, null));
        check(!quads.isEmpty() && quads.size() <= (id.getPath().equals("storage_nexus") ? 100 : 80), id + " exceeds its low-poly budget or is empty: " + quads.size());
        int crystals = 0, plain = 0; Set<String> faces = new HashSet<>();
        for (BakedQuad quad : quads) {
            var sprite = quad.getSprite().contents().name();
            check(sprite.getNamespace().equals(AstralRepository.MOD_ID) && SPRITES.contains(sprite.getPath()), id + " uses a missing or noncustom sprite: " + sprite);
            if (CrystalModelRenderer.isCrystal(quad)) crystals++; else plain++;
            int[] data = quad.getVertices(); int stride = data.length / 4; List<String> points = new ArrayList<>(); Vector3f[] vertices = new Vector3f[4];
            for (int v = 0; v < 4; v++) {
                vertices[v] = new Vector3f(Float.intBitsToFloat(data[v * stride]), Float.intBitsToFloat(data[v * stride + 1]), Float.intBitsToFloat(data[v * stride + 2]));
                check(vertices[v].isFinite(), id + " has nonfinite vertices");
                points.add(Math.round(vertices[v].x * 100000) + ":" + Math.round(vertices[v].y * 100000) + ":" + Math.round(vertices[v].z * 100000));
            }
            Vector3f normal = new Vector3f(vertices[1]).sub(vertices[0]).cross(new Vector3f(vertices[2]).sub(vertices[0]));
            check(normal.lengthSquared() > 1e-10, id + " has a degenerate first triangle"); normal.normalize();
            check(normal.dot(new Vector3f(quad.getDirection().getStepX(), quad.getDirection().getStepY(), quad.getDirection().getStepZ())) > .45F, id + " has reversed winding or incorrect face normal");
            Collections.sort(points); check(faces.add(String.join("/", points)), id + " duplicates a coincident face");
        }
        check(crystals > 0 && plain > 0, id + " lacks separate crystal and plain material faces"); return quads.size();
    }

    private enum Layer { ALL, CRYSTAL, PLAIN }
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };
    private static void drawNode(int index, boolean world, float yaw, float x, float y, float size, PoseStack poses, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance(); poses.pushPose();
        try {
            poses.translate(x, y, 220); poses.scale(size, -size, size); poses.mulPose(Axis.YP.rotationDegrees(yaw));
            RenderSystem.enableDepthTest(); RenderSystem.enableCull(); Lighting.setupFor3DItems();
            if (world) {
                poses.mulPose(Axis.XP.rotationDegrees(25)); poses.mulPose(Axis.YP.rotationDegrees(45)); poses.translate(-.5, -.5, -.5);
                var entity = (CrystalNodeBlockEntity)mc.level.getBlockEntity(position(index));
                var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(entity); check(renderer != null, "Node world renderer is missing at " + position(index));
                renderer.render(entity, 0, poses, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } else mc.getItemRenderer().renderStatic(stack(index), ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poses, buffers, mc.level, 0);
        } finally { poses.popPose(); }
    }
    private static void verifyGpu() throws Exception {
        check(AstralPlaneRenderType.ready(), "Node shader is not loaded"); GlSnapshot gl = new GlSnapshot();
        double opacity = AstralClientConfig.astralOverlayOpacity.get(); TextureTarget target = null; Map<String, NativeImage> images = new LinkedHashMap<>();
        RenderSystem.getModelViewStack().pushMatrix();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(16384)) {
            RenderSystem.disableScissor(); RenderSystem.colorMask(true, true, true, true); RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.setShaderFogStart(1000); RenderSystem.setShaderFogEnd(2000);
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX); var buffers = MultiBufferSource.immediate(memory);
            StringBuilder report = new StringBuilder("Real ItemRenderer.GUI and registered world BER;512x512FBO; frozen GameTime4200; fullbright; culling enabled.\n");
            List<String> failures = new ArrayList<>();
            for (boolean world : List.of(false, true)) for (int yaw : List.of(0, 180)) {
                String prefix = (world ? "world" : "item") + "_" + yaw;
                NativeImage zero = renderGrid(target, buffers, world, yaw, Layer.ALL, 0, prefix + "_zero", images);
                NativeImage full = renderGrid(target, buffers, world, yaw, Layer.ALL, 1, prefix + "_full", images);
                NativeImage crystal = renderGrid(target, buffers, world, yaw, Layer.CRYSTAL, 0, prefix + "_crystal_mask", images);
                NativeImage plain = renderGrid(target, buffers, world, yaw, Layer.PLAIN, 0, prefix + "_plain_mask", images);
                for (int index = 0; index < VARIANTS; index++) {
                    int crystalPixels = 0, plainPixels = 0, changedCrystals = 0, changedPlain = 0, escaped = 0;
                    int left = index % 4 * CELL, top = index / 4 * CELL;
                    for (int y = top; y < top + CELL; y++) for (int x = left; x < left + CELL; x++) {
                        boolean crystalPixel = (crystal.getPixelRGBA(x, y) >>> 24) != 0, plainPixel = (plain.getPixelRGBA(x, y) >>> 24) != 0;
                        boolean changed = (zero.getPixelRGBA(x, y) & 0xffffff) != (full.getPixelRGBA(x, y) & 0xffffff);
                        if (crystalPixel) { crystalPixels++; if (channelError(zero.getPixelRGBA(x, y), full.getPixelRGBA(x, y)) > 2) changedCrystals++; }
                        else { if (plainPixel) { plainPixels++; if (changed) changedPlain++; } if (changed) escaped++; }
                    }
                    String id = name(index);
                    report.append(prefix).append('/').append(id).append(" crystal=").append(crystalPixels).append(" stone_trim=").append(plainPixels)
                            .append(" changed_crystal=").append(changedCrystals).append(" changed_plain=").append(changedPlain).append(" escaped=").append(escaped).append('\n');
                    if (crystalPixels < 5 || plainPixels < 20 || changedCrystals < 5 || changedPlain != 0 || escaped != 0) failures.add(prefix + "/" + id + " has missing geometry or incorrect opacity partition");
                }
            }
            RenderSystem.enableCull(); AstralPlaneRenderType.ASTRAL_OVERLAY.setupRenderState();
            try { check(GL11.glIsEnabled(GL11.GL_CULL_FACE), "Node astral material disables face culling"); } finally { AstralPlaneRenderType.ASTRAL_OVERLAY.clearRenderState(); }
            Files.writeString(OUT.resolve("gpu-metrics.txt"), report); AstralRepository.LOGGER.info("Node model GPU measurements before assertions:\n{}", report);
            check(failures.isEmpty(), String.join("; ", failures));
        } finally {
            AstralClientConfig.astralOverlayOpacity.set(opacity); images.values().forEach(NativeImage::close);
            AstralPlaneRenderType.ASTRAL_OVERLAY.setupRenderState(); AstralPlaneRenderType.ASTRAL_OVERLAY.clearRenderState();
            if (target != null) target.destroyBuffers(); RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix(); gl.restore();
        }
    }
    private static NativeImage renderGrid(TextureTarget target, MultiBufferSource.BufferSource buffers, boolean world, int yaw, Layer layer, double opacity, String name, Map<String, NativeImage> images) throws Exception {
        AstralClientConfig.astralOverlayOpacity.set(opacity); target.setClearColor(.03F, .04F, .06F, layer == Layer.ALL ? 1 : 0);
        RenderSystem.depthMask(true); target.clear(Minecraft.ON_OSX); target.bindWrite(true); RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z); RenderSystem.setShaderGameTime(4200, 0);
        RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.enableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        MultiBufferSource selected = type -> layer == Layer.ALL || (type == AstralPlaneRenderType.ASTRAL_OVERLAY) == (layer == Layer.CRYSTAL) ? buffers.getBuffer(type) : DISCARD;
        for (int index = 0; index < VARIANTS; index++) drawNode(index, world, yaw, index % 4 * CELL + 64, index / 4 * CELL + 64, world ? 72 : 88, new PoseStack(), selected);
        buffers.endBatch(); NativeImage image = new NativeImage(SIZE, SIZE, false);
        RenderSystem.bindTexture(target.getColorTextureId()); image.downloadTexture(0, false); image.flipY(); images.put(name, image); image.writeToFile(OUT.resolve(name + ".png")); return image;
    }
    private static int channelError(int a, int b) { int error = 0; for (int shift = 0; shift < 24; shift += 8) error = Math.max(error, Math.abs((a >>> shift & 255) - (b >>> shift & 255))); return error; }

    private static final class Gallery extends Screen {
        private float yaw;
        Gallery() { super(Component.literal("Astral node models")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff111625); graphics.drawCenteredString(font, yaw == 0 ? "Astral Repository · block items" : "Astral Repository · reverse / underside", width / 2, 8, 0xd9defb);
            float cellWidth = width / 4F, cellHeight = (height - 30) / 3F, size = Math.min(cellWidth - 25, cellHeight - 27);
            for (int i = 0; i < VARIANTS; i++) {
                float x = (i % 4 + .5F) * cellWidth, y = 30 + (i / 4 + .5F) * cellHeight;
                graphics.fill((int)(x - cellWidth / 2 + 5), (int)(y - cellHeight / 2 + 3), (int)(x + cellWidth / 2 - 5), (int)(y + cellHeight / 2 - 3), 0xff20283b);
                var lines = font.split(Component.literal(name(i)), Math.max(1, (int)cellWidth - 12));
                int lineCount = Math.min(2, lines.size()), labelTop = (int)(y + cellHeight / 2 - 8 - lineCount * font.lineHeight);
                for (int line = 0; line < lineCount; line++) graphics.drawCenteredString(font, lines.get(line), (int)x, labelTop + line * font.lineHeight, 0xd9defb);
                graphics.flush(); drawNode(i, false, yaw, x, y - 7, size, graphics.pose(), graphics.bufferSource()); graphics.flush();
            }
        }
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
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
