package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.client.AstralMineralClient;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.AstralMineralBlockEntity;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Client-development assertions and real render previews. The release JAR excludes gametest classes. */
public final class MaterialGeometrySmoke {
    private static final float EPSILON = 0.00001F;
    private static final ResourceLocation WAND = ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID, "item/attunement_wand");
    private static final ResourceLocation WAND_BITMAP = ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID, "textures/item/attunement_wand.png");
    private static final ResourceLocation REMOTE_BITMAP = ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID, "textures/item/astral_nexus.png");
    private static List<WandProbe> wandBaseline = List.of();
    private static List<RemoteProbe> remoteBaseline = List.of();
    private MaterialGeometrySmoke() {}

    public static void verify() {
        requireDevelopmentClient();
        Minecraft client = Minecraft.getInstance();
        check(AstralPlaneRenderType.ready(), "Astral material shader must be loaded");
        BakedModel wand = client.getModelManager().getModel(AstralMineralClient.WAND_MODEL);
        List<BakedQuad> wandQuads = allQuads(wand, null);
        check(!wandQuads.isEmpty(), "Wand geometry is missing");
        float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
        Set<String> vertexSets = new HashSet<>();
        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);
        Set<ResourceLocation> sprites = new HashSet<>();
        for (BakedQuad quad : wandQuads) {
            Geometry geometry = geometry(quad);
            check(vertexSets.add(geometry.vertexSet()), "Wand has coincident duplicate faces: " + geometry.vertexSet());
            for (Vector3f vertex : geometry.vertices()) { minimum = Math.min(minimum, vertex.z()); maximum = Math.max(maximum, vertex.z()); }
            Direction direction = Direction.getNearest(geometry.normal().x(), geometry.normal().y(), geometry.normal().z());
            check(direction == quad.getDirection(), "Wand face winding disagrees with its baked normal");
            check(geometry.normal().dot(new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ())) > 0.999F, "Wand outline normal is not axis-aligned");
            directions.add(direction); sprites.add(quad.getSprite().contents().name());
        }
        check(Math.abs(minimum - 7.5F / 16) < EPSILON && Math.abs(maximum - 8.5F / 16) < EPSILON,
                "Wand must retain exactly one model pixel of depth; found " + minimum + ".." + maximum);
        check(directions.equals(EnumSet.allOf(Direction.class)), "Wand is missing front, back, or extruded outline faces: " + directions);
        check(sprites.equals(Set.of(WAND)), "Every wand face must use the supplied wand bitmap: " + sprites);
        verifyWandMask(wandQuads);
        BakedModel remoteItem=client.getItemRenderer().getModel(new ItemStack(AstralContent.ASTRAL_NEXUS.get()),client.level,client.player,0);
        check(remoteItem.isCustomRenderer(),"Remote access item must dispatch its astral overlay renderer");
        BakedModel remote=client.getModelManager().getModel(AstralMineralClient.REMOTE_MODEL);
        check(!remote.isCustomRenderer()&&!remote.isGui3d(),"Remote overlay must retain flat generated underlying geometry");
        var remoteQuads=allQuads(remote,null);EnumSet<Direction> remoteSides=EnumSet.noneOf(Direction.class);
        check(!remoteQuads.isEmpty(),"Remote access item geometry is missing");
        for(var quad:remoteQuads){remoteSides.add(quad.getDirection());check(quad.getSprite().contents().name().equals(ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID,"item/astral_nexus")),"Remote must use its original Astral Nexus bitmap");for(var point:geometry(quad).vertices())check(point.z()>=7.5F/16-EPSILON&&point.z()<=8.5F/16+EPSILON,"Remote must stay one pixel thick");}
        check(remoteSides.equals(EnumSet.allOf(Direction.class)),"Remote tool lacks filled silhouette edges");
        verifyNaturalSprites(client);
        verifyCullState();
        int states = 0;
        for (Block block : buds()) for (Direction facing : Direction.values()) {
            BlockState state = block.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
            verifyCross(client.getBlockRenderer().getBlockModel(state), state); states++;
        }
        AstralRepository.LOGGER.info("Material geometry checks passed: {} wand quads, six outline directions, one-pixel depth, {} crossed bud states, and enabled material culling", wandQuads.size(), states);
    }

    private static void verifyNaturalSprites(Minecraft client) {
        for(var block:List.of(AstralContent.ASTRAL_GEODE.get(),AstralContent.BUDDING_ASTRAL.get(),
                AstralContent.SMALL_ASTRAL_BUD.get(),AstralContent.MEDIUM_ASTRAL_BUD.get(),
                AstralContent.LARGE_ASTRAL_BUD.get(),AstralContent.ASTRAL_CLUSTER.get())) {
            var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            var expected=ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID,"block/"+id.getPath());
            var state=block.defaultBlockState();
            var quads=allQuads(client.getBlockRenderer().getBlockModel(state),state);
            check(!quads.isEmpty()&&quads.stream().allMatch(q->q.getSprite().contents().name().equals(expected)),
                    "Natural crystal must use its custom sprite: "+id);
        }
        var gem=allQuads(client.getModelManager().getModel(AstralMineralClient.GEM_MODEL),null);
        check(!gem.isEmpty()&&gem.stream().allMatch(q->q.getSprite().contents().name().equals(
                ResourceLocation.fromNamespaceAndPath(AstralRepository.MOD_ID,"item/astral_gem"))),
                "Astral Gem must use its own item artwork");
    }

    private static void verifyCross(BakedModel model, BlockState state) {
        var random = RandomSource.create(42);
        for (Direction direction : Direction.values()) {
            random.setSeed(42);
            check(model.getQuads(state, direction, random).isEmpty(), "Crossed bud unexpectedly has directional quad lists: " + state);
        }
        random.setSeed(42);
        List<BakedQuad> quads = model.getQuads(state, null, random);
        check(quads.size() == 4, "Crossed bud must have four general quads: " + state);
        Map<String, List<Geometry>> surfaces = new HashMap<>();
        for (BakedQuad quad : quads) {
            Geometry geometry = geometry(quad);
            surfaces.computeIfAbsent(geometry.vertexSet(), ignored -> new ArrayList<>()).add(geometry);
        }
        check(surfaces.size() == 2, "Crossed bud must have two geometric planes: " + state);
        for (List<Geometry> pair : surfaces.values()) {
            check(pair.size() == 2 && pair.get(0).normal().dot(pair.get(1).normal()) < -0.999F, "Crossed bud needs opposite winding on each coplanar pair: " + state);
            for (Vector3f view : List.of(new Vector3f(1, 2, 3), new Vector3f(-2, 3, 5), new Vector3f(4, -3, 1))) {
                long frontFacing = pair.stream().filter(face -> face.normal().dot(view) > EPSILON).count();
                check(frontFacing == 1, "Exactly one side of each crossed plane must face an oblique viewer: " + state);
            }
        }
    }

    private static void verifyCullState() {
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var shader = RenderSystem.getShader();
        int[] shaderTextures = { RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(1), RenderSystem.getShaderTexture(2) };
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        atlas.setBlurMipmap(false, false);
        try {
            // Vanilla CULL preserves the normal enabled baseline; NO_CULL disables it during setup.
            RenderSystem.enableCull();
            AstralPlaneRenderType.ASTRAL_PLANE.setupRenderState();
            check(GL11.glIsEnabled(GL11.GL_CULL_FACE), "Astral material disables culling and renders both sides of crossed bud planes");
        } finally {
            AstralPlaneRenderType.ASTRAL_PLANE.clearRenderState();
            atlas.restoreLastBlurMipmap();
            RenderSystem.setShader(() -> shader);
            for (int i = 0; i < shaderTextures.length; i++) RenderSystem.setShaderTexture(i, shaderTextures[i]);
            RenderSystem.activeTexture(activeTexture); RenderSystem.bindTexture(textureBinding);
            RenderSystem.depthFunc(depthFunction);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }

    private record Geometry(List<Vector3f> vertices, Vector3f normal, String vertexSet) {}
    private static Geometry geometry(BakedQuad quad) {
        int[] data = quad.getVertices(); int stride = data.length / 4;
        check(data.length % 4 == 0 && stride >= 3, "Invalid baked quad format");
        List<Vector3f> vertices = new ArrayList<>(4);
        List<String> keys = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            Vector3f point = new Vector3f(Float.intBitsToFloat(data[i * stride]), Float.intBitsToFloat(data[i * stride + 1]), Float.intBitsToFloat(data[i * stride + 2]));
            check(Float.isFinite(point.x()) && Float.isFinite(point.y()) && Float.isFinite(point.z()), "Non-finite baked vertex");
            vertices.add(point);
            keys.add(Math.round(point.x() * 100000) + ":" + Math.round(point.y() * 100000) + ":" + Math.round(point.z() * 100000));
        }
        Vector3f normal = new Vector3f(vertices.get(1)).sub(vertices.get(0)).cross(new Vector3f(vertices.get(2)).sub(vertices.get(0)));
        check(normal.lengthSquared() > 1.0E-12F, "Degenerate baked face"); normal.normalize();
        check(Math.abs(normal.dot(new Vector3f(vertices.get(3)).sub(vertices.get(0)))) < EPSILON, "Non-planar baked face");
        Collections.sort(keys);
        return new Geometry(List.copyOf(vertices), normal, String.join("/", keys));
    }
    private static List<BakedQuad> allQuads(BakedModel model, BlockState state) {
        List<BakedQuad> quads = new ArrayList<>(); var random = RandomSource.create(42);
        for (Direction direction : Direction.values()) { random.setSeed(42); quads.addAll(model.getQuads(state, direction, random)); }
        random.setSeed(42); quads.addAll(model.getQuads(state, null, random)); return quads;
    }
    private static List<Block> buds() { return List.of(AstralContent.SMALL_ASTRAL_BUD.get(), AstralContent.MEDIUM_ASTRAL_BUD.get(), AstralContent.LARGE_ASTRAL_BUD.get(), AstralContent.ASTRAL_CLUSTER.get()); }
    private static void requireDevelopmentClient() {
        check(Boolean.getBoolean("astral_repository.testClient"), "Material geometry smoke is development-client-only");
        RenderSystem.assertOnRenderThread();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private enum WandRegion { CRYSTAL, BODY, POMMEL }
    private record WandProbe(int x, int y, int baseline, WandRegion region) {}
    private record RemoteProbe(int x, int y, int baseline, int quadrant) {}

    // Independent fixture coordinates from the supplied 34x36 bitmap; the four pommel
    // samples are its small blue gem, never part of the large crystal's astral mask.
    private static WandRegion wandRegion(int x, int y) {
        if ((x == 2 || x == 3) && (y == 31 || y == 32)) return WandRegion.POMMEL;
        int[] left = { 30, 27, 25, 23, 22, 21, 20, 20, 19, 19, 19, 19, 19, 19, 19, 20 };
        int[] right = { 32, 32, 32, 32, 31, 31, 31, 30, 30, 30, 29, 29, 28, 26, 24, 22 };
        return y >= 1 && y <= 16 && x >= left[y - 1] && x <= right[y - 1] ? WandRegion.CRYSTAL : WandRegion.BODY;
    }

    private static void verifyWandMask(List<BakedQuad> quads) {
        try (var stream = Minecraft.getInstance().getResourceManager().open(WAND_BITMAP); NativeImage source = NativeImage.read(stream)) {
            check(source.getWidth() == 34 && source.getHeight() == 36, "Wand fixture must inspect the supplied 34x36 bitmap");
            int crystal = 0, body = 0;
            for (BakedQuad quad : quads) {
                int[] data = quad.getVertices(); int stride = data.length / 4;
                double u = 0, v = 0;
                for (int i = 0; i < 4; i++) { u += Float.intBitsToFloat(data[i * stride + 4]); v += Float.intBitsToFloat(data[i * stride + 5]); }
                var sprite = quad.getSprite();
                int x = Math.clamp((int)Math.floor((u / 4 - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * source.getWidth()), 0, source.getWidth() - 1);
                int y = Math.clamp((int)Math.floor((v / 4 - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * source.getHeight()), 0, source.getHeight() - 1);
                WandRegion region = wandRegion(x, y);
                check(quad.getTintIndex() == (region == WandRegion.CRYSTAL ? 0 : -1), "Wand shader mask includes the wrong bitmap region at " + x + "," + y);
                if (region == WandRegion.CRYSTAL) crystal++; else body++;
            }
            check(crystal > 0 && body > 0, "Wand geometry must include independent crystal and plain bitmap materials");
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getPixelRGBA(x, y) >>> 24) < 250) continue;
                float modelX = (x + 1.5F) / 36, modelY = 1 - (y + .5F) / 36;
                int matches = 0;
                for (BakedQuad quad : quads) if (quad.getDirection() == Direction.SOUTH) {
                    var points = geometry(quad).vertices();
                    float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
                    for (var point : points) { minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x()); minY = Math.min(minY, point.y()); maxY = Math.max(maxY, point.y()); }
                    if (modelX > minX + EPSILON && modelX < maxX - EPSILON && modelY > minY + EPSILON && modelY < maxY - EPSILON) {
                        matches++;
                        check(quad.getTintIndex() == (wandRegion(x, y) == WandRegion.CRYSTAL ? 0 : -1), "Wand pixel has the wrong crystal mask at " + x + "," + y);
                    }
                }
                check(matches == 1, "Supplied wand pixel must have exactly one front face at " + x + "," + y + "; found " + matches);
            }
        } catch (java.io.IOException failure) { throw new AssertionError("Cannot inspect the supplied wand bitmap", failure); }
    }

    public static Screen wandOpacityPreview() { requireDevelopmentClient(); return new WandOpacityPreview(); }

    public static void captureWandOpacityBaseline() throws java.io.IOException {
        requireDevelopmentClient();
        Minecraft client = Minecraft.getInstance();
        check(client.screen instanceof WandOpacityPreview, "Wand opacity comparison requires its isolated preview");
        check(Math.abs(AstralPlaneRenderType.appliedOverlayOpacity()) < 0.001, "Wand baseline was not rendered at zero opacity");
        try (var stream = client.getResourceManager().open(WAND_BITMAP); NativeImage source = NativeImage.read(stream); NativeImage frame = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            var probes = new ArrayList<WandProbe>();
            float size = WandOpacityPreview.itemSize(client.screen.width, client.screen.height);
            double scaleX = (double)frame.getWidth() / client.screen.width, scaleY = (double)frame.getHeight() / client.screen.height;
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getPixelRGBA(x, y) >>> 24) < 250) continue;
                int px = (int)Math.floor((client.screen.width / 4F + size * ((x + 1.5) / 36 - .5)) * scaleX);
                int py = (int)Math.floor((client.screen.height / 2F + 10 + size * ((y + .5) / source.getHeight() - .5)) * scaleY);
                check(px >= 0 && px < frame.getWidth() && py >= 0 && py < frame.getHeight(), "Wand probe is outside the framebuffer");
                int color = frame.getPixelRGBA(px, py);
                check((color & 0xFFFFFF) != 0x1E1410, "Opaque wand pixel was not rendered at " + x + "," + y);
                probes.add(new WandProbe(px, py, color, wandRegion(x, y)));
            }
            for (WandRegion region : WandRegion.values()) check(probes.stream().filter(probe -> probe.region() == region).count() >= 3, "Too few rendered wand samples for " + region);
            wandBaseline = List.copyOf(probes);
        }
        captureRemoteOpacityBaseline();
    }

    private static void captureRemoteOpacityBaseline() throws java.io.IOException {
        Minecraft client = Minecraft.getInstance();
        try (var stream = client.getResourceManager().open(REMOTE_BITMAP); NativeImage source = NativeImage.read(stream); NativeImage frame = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            check(source.getWidth() == 47 && source.getHeight() == 47, "Remote fixture must inspect the supplied 47x47 orb");
            var probes = new ArrayList<RemoteProbe>();
            float size = WandOpacityPreview.itemSize(client.screen.width, client.screen.height);
            double scaleX = (double)frame.getWidth() / client.screen.width, scaleY = (double)frame.getHeight() / client.screen.height;
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getPixelRGBA(x, y) >>> 24) < 250) continue;
                int px = (int)Math.floor((client.screen.width * 3 / 4F + size * ((x + .5) / source.getWidth() - .5)) * scaleX);
                int py = (int)Math.floor((client.screen.height / 2F + 10 + size * ((y + .5) / source.getHeight() - .5)) * scaleY);
                check(px >= 0 && px < frame.getWidth() && py >= 0 && py < frame.getHeight(), "Remote probe is outside the framebuffer");
                int color = frame.getPixelRGBA(px, py);
                check((color & 0xFFFFFF) != 0x1E1410, "Opaque remote pixel was not rendered at " + x + "," + y);
                int quadrant = (x >= source.getWidth() / 2 ? 1 : 0) + (y >= source.getHeight() / 2 ? 2 : 0);
                probes.add(new RemoteProbe(px, py, color, quadrant));
            }
            check(probes.size() > 100, "Remote comparison lacks opaque orb samples");
            remoteBaseline = List.copyOf(probes);
        }
    }

    public static void verifyWandOpacityIsolation() {
        requireDevelopmentClient();
        check(Minecraft.getInstance().screen instanceof WandOpacityPreview && !wandBaseline.isEmpty(), "Wand opacity baseline is missing");
        check(Math.abs(AstralPlaneRenderType.appliedOverlayOpacity() - 1) < 0.001, "Wand comparison was not rendered at full opacity");
        try (NativeImage frame = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            var sums = new EnumMap<WandRegion, Double>(WandRegion.class);
            var counts = new EnumMap<WandRegion, Integer>(WandRegion.class);
            for (WandProbe probe : wandBaseline) {
                double difference = rgbDelta(probe.baseline(), frame.getPixelRGBA(probe.x(), probe.y()));
                sums.merge(probe.region(), difference, Double::sum); counts.merge(probe.region(), 1, Integer::sum);
                if (probe.region() != WandRegion.CRYSTAL) check(difference <= 3.0 / 255, "Astral opacity changed an untinted " + probe.region() + " pixel at " + probe.x() + "," + probe.y() + " by " + difference);
            }
            double crystal = sums.get(WandRegion.CRYSTAL) / counts.get(WandRegion.CRYSTAL);
            check(crystal > .035, "Astral opacity did not visibly change the large wand crystal: " + crystal);
            AstralRepository.LOGGER.info("Wand framebuffer opacity isolation passed: {} large-crystal samples changed by {}, {} frame/grip pixels and {} pommel pixels stayed stable", counts.get(WandRegion.CRYSTAL), crystal, counts.get(WandRegion.BODY), counts.get(WandRegion.POMMEL));
            wandBaseline = List.of();
            check(!remoteBaseline.isEmpty(), "Remote opacity baseline is missing");
            double[] remoteChange = new double[4]; int[] remoteCount = new int[4];
            for (RemoteProbe probe : remoteBaseline) {
                remoteChange[probe.quadrant()] += rgbDelta(probe.baseline(), frame.getPixelRGBA(probe.x(), probe.y()));
                remoteCount[probe.quadrant()]++;
            }
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                check(remoteCount[quadrant] > 10, "Remote quadrant lacks visible orb pixels: " + quadrant);
                check(remoteChange[quadrant] / remoteCount[quadrant] > .035, "Astral opacity did not visibly change remote orb quadrant " + quadrant);
            }
            AstralRepository.LOGGER.info("Remote framebuffer opacity comparison passed: {} supplied orb pixels changed across all four quadrants", remoteBaseline.size());
            remoteBaseline = List.of();
        }
    }

    private static double rgbDelta(int first, int second) {
        int total = 0;
        for (int shift : new int[] { 0, 8, 16 }) total += Math.abs(((first >>> shift) & 255) - ((second >>> shift) & 255));
        return total / (3.0 * 255);
    }

    private static final class WandOpacityPreview extends Screen {
        WandOpacityPreview() { super(Component.literal("Supplied item shader comparison")); }
        @Override public boolean isPauseScreen() { return false; }
        static float itemSize(int width, int height) { return Math.min(width / 2F - 45, height - 90); }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xFF10141E);
            g.drawCenteredString(font, "Supplied item textures: configurable astral overlay", width / 2, 12, 0xE7EBF6);
            g.drawCenteredString(font, "Wand: large crystal only", width / 4, 30, 0xADB9CE);
            g.drawCenteredString(font, "Remote: whole orb", width * 3 / 4, 30, 0xADB9CE);
            g.flush();
            renderItem(g, new ItemStack(AstralContent.ATTUNEMENT_WAND.get()), width / 4F, height / 2F + 10, itemSize(width, height), 0, 0);
            renderItem(g, new ItemStack(AstralContent.ASTRAL_NEXUS.get()), width * 3 / 4F, height / 2F + 10, itemSize(width, height), 0, 0);
        }
    }

    static void renderItem(GuiGraphics g, ItemStack stack, float x, float y, float size, float yaw, float pitch) {
        renderItem(g, stack, x, y, size, yaw, pitch, ItemDisplayContext.GUI);
    }

    static void renderItem(GuiGraphics g, ItemStack stack, float x, float y, float size, float yaw, float pitch, ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
        var poses = g.pose(); poses.pushPose();
        try {
            // Same GUI-space translation, Y inversion, lighting, and item-renderer entry as GuiGraphics.renderItem.
            poses.translate(x, y, 180); poses.scale(size, -size, size);
            poses.mulPose(Axis.YP.rotationDegrees(yaw)); poses.mulPose(Axis.XP.rotationDegrees(pitch));
            Lighting.setupForFlatItems(); RenderSystem.enableDepthTest(); RenderSystem.enableCull();
            minecraft.getItemRenderer().render(stack, context, false, poses, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            g.bufferSource().endBatch();
        } finally { poses.popPose(); Lighting.setupFor3DItems(); }
    }

    public static Screen preview() { requireDevelopmentClient(); return new Preview(); }
    private static final class Preview extends Screen {
        Preview() { super(Component.literal("Material geometry preview")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xFF10141E);
            g.drawCenteredString(font, "Actual material geometry", width / 2, 12, 0xE7EBF6);
            g.drawCenteredString(font, "Wand angles and remote access tool", width / 2, 27, 0xADB9CE);
            float wandSize = Math.min(width / 4F - 30, height * 0.31F);
            int wandCenterY = Math.round(height * 0.30F);
            String[] labels = { "Front · 0°", "Oblique · 55°", "Edge-on · 90°", "Remote tool" };
            for (int i = 0; i < 4; i++) {
                int center = Math.round(width * (i + 0.5F) / 4F);
                int left = Math.round(center - wandSize / 2 - 10), top = Math.round(wandCenterY - wandSize / 2 - 5);
                g.fill(left, top, Math.round(center + wandSize / 2 + 10), Math.round(wandCenterY + wandSize / 2 + 10), 0xFF202737);
                g.drawCenteredString(font, labels[i], center, Math.round(wandCenterY + wandSize / 2 + 17), 0xD1D9E8);
            }
            int budHeading = Math.round(height * 0.59F);
            g.drawCenteredString(font, "Crossed world buds · item thumbnails below", width / 2, budHeading, 0xADB9CE);
            int budCenterY = Math.round(height * 0.76F);
            float budSize = Math.min(width / 4F - 28, height * 0.19F);
            String[] stages = { "Small", "Medium", "Large", "Cluster" };
            for (int i = 0; i < 4; i++) {
                int center = Math.round(width * (i + 0.5F) / 4F);
                g.fill(Math.round(center - budSize / 2 - 8), budHeading + 15, Math.round(center + budSize / 2 + 8), Math.round(height * 0.94F), 0xFF202737);
                g.drawCenteredString(font, stages[i], center, budHeading + 19, 0xD1D9E8);
            }
            g.flush();
            ItemStack wand = new ItemStack(AstralContent.ATTUNEMENT_WAND.get());
            renderItem(g, wand, width / 8F, wandCenterY, wandSize, 0, 0);
            renderItem(g, wand, width * 3 / 8F, wandCenterY, wandSize, 55, 12);
            renderItem(g, wand, width * 5 / 8F, wandCenterY, wandSize, 90, 0);
            renderItem(g,new ItemStack(AstralContent.ASTRAL_NEXUS.get()),width*7/8F,wandCenterY,wandSize,0,0);
            for (int i = 0; i < 4; i++) {
                Block block = buds().get(i); float center = width * (i + 0.5F) / 4F;
                renderWorldBud(g, block, center, budCenterY, budSize, partialTick);
                renderItem(g, new ItemStack(block), center, height * 0.91F, 18, 0, 0);
            }

        }
        private void renderWorldBud(GuiGraphics g, Block block, float x, float y, float size, float partialTick) {
            var entity = new AstralMineralBlockEntity(BlockPos.ZERO, block.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
            var renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(entity);
            check(renderer != null, "Actual mineral block renderer is missing");
            var poses = g.pose(); poses.pushPose();
            try {
                poses.translate(x, y, 180); poses.scale(size, -size, size);
                poses.mulPose(Axis.XP.rotationDegrees(18)); poses.mulPose(Axis.YP.rotationDegrees(32)); poses.translate(-0.5, -0.5, -0.5);
                Lighting.setupForFlatItems(); RenderSystem.enableDepthTest(); RenderSystem.enableCull();
                renderer.render(entity, partialTick, poses, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                g.bufferSource().endBatch();
            } finally { poses.popPose(); Lighting.setupFor3DItems(); }
        }
    }
}



