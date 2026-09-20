package com.cappleapple.astralrepository.client;

import com.cappleapple.astralrepository.api.ItemKey;
import com.cappleapple.astralrepository.content.AstralContent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Cached GUI silhouettes for bulk flights; no item renderer or framebuffer work per draw. */
final class TransferItemSprites extends RenderType {
    private static final int MAX_MESHES = 256, MAX_IDENTITIES = 8192, MAX_QUADS = 8, MAX_NEW_MESHES = 4, MAX_MODEL_RESOLUTIONS = 16;
    private static final Direction[] DIRECTIONS = Direction.values();
    private record Mesh(float[] coordinates, int[] colors) { int vertices() { return colors.length; } }
    private record Base(BakedModel model, Mesh fallback) {}
    private static final class Lookup {
        final ItemKey key;
        BakedModel model;
        Mesh fallback;
        Lookup(ItemStack stack) { key = new ItemKey(stack); }
    }
    // Packet stacks are immutable snapshots. Avoid hashing/copying components for each frame.
    // Keep only a tiny fallback per live identity; an evicted detailed mesh is never retained here.
    private static final Map<ItemStack, Lookup> IDENTITIES = new IdentityHashMap<>();
    private static final ArrayDeque<ItemStack> IDENTITY_ORDER = new ArrayDeque<>();
    private static final Map<ItemKey, Mesh> MESHES = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ItemKey, Mesh> eldest) { return size() > MAX_MESHES; }
    };
    private static final Map<ItemKey, Base> BASES = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ItemKey, Base> eldest) { return size() > MAX_MESHES; }
    };
    private static final RenderType MATERIAL = create("astral_repository:transfer_item_icons", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 262144, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setOverlayState(OVERLAY).setOutputState(PARTICLES_TARGET).setWriteMaskState(TransferRenderPass.SPRITE_WRITE).createCompositeState(false));
    private static int buildsRemaining, resolutionsRemaining, modelResolutions, renderedVertices, identitiesRemaining;

    static void beginFrame() { identitiesRemaining = 128; buildsRemaining = MAX_NEW_MESHES; resolutionsRemaining = MAX_MODEL_RESOLUTIONS; modelResolutions = 0; renderedVertices = 0; }
    static int renderedVertices() { return renderedVertices; }
    static int modelResolutions() { return modelResolutions; }
    static void clearCache() { MESHES.clear(); BASES.clear(); IDENTITIES.clear(); IDENTITY_ORDER.clear(); buildsRemaining = 0; resolutionsRemaining = 0; modelResolutions = 0; }
    static void flush(MultiBufferSource.BufferSource buffers) { buffers.endBatch(MATERIAL); }

    static boolean render(ItemStack stack, Vec3 position, PoseStack poses, MultiBufferSource.BufferSource buffers,
            Quaternionf camera, float size) {
        if (stack.isEmpty()) return false;
        Mesh mesh = mesh(stack); if (mesh == null) return false;
        var vertices = buffers.getBuffer(MATERIAL);
        poses.pushPose(); poses.translate(position.x, position.y, position.z); poses.mulPose(camera);
        float scale = size * 2;
        for (int i = 0; i < mesh.vertices(); i++) {
            int offset = i * 5;
            vertices.addVertex(poses.last().pose(), mesh.coordinates[offset] * scale, mesh.coordinates[offset + 1] * scale,
                            mesh.coordinates[offset + 2] * scale)
                    .setColor(mesh.colors[i]).setUv(mesh.coordinates[offset + 3], mesh.coordinates[offset + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(poses.last(), 0, 0, 1);
        }
        poses.popPose(); renderedVertices += mesh.vertices(); return true;
    }

    private static Mesh mesh(ItemStack stack) {
        Lookup lookup = IDENTITIES.get(stack);
        if (lookup == null) {
            if (identitiesRemaining-- <= 0) return null;
            lookup = new Lookup(stack); IDENTITIES.put(stack, lookup); IDENTITY_ORDER.addLast(stack);
            if (IDENTITY_ORDER.size() > MAX_IDENTITIES) IDENTITIES.remove(IDENTITY_ORDER.removeFirst());
        }
        Mesh cached = MESHES.get(lookup.key);
        if (cached != null) return cached;
        if (lookup.model == null) {
            Base base = BASES.get(lookup.key);
            if (base == null) {
                if (resolutionsRemaining <= 0) return null;
                resolutionsRemaining--; modelResolutions++;
                var mc = Minecraft.getInstance(); var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                if (stack.is(Items.TRIDENT) || stack.is(Items.SPYGLASS))
                    model = mc.getModelManager().getModel(ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(stack.getItem())));
                base = new Base(model, fallback(stack, model)); BASES.put(lookup.key, base);
            }
            lookup.model = base.model; lookup.fallback = base.fallback;
        }
        if (buildsRemaining > 0) {
            buildsRemaining--;
            try { cached = flatten(stack, lookup.model); }
            catch (RuntimeException ignored) { /* Nonstandard model implementations retain their particle icon. */ }
            if (cached == null) cached = lookup.fallback;
            MESHES.put(lookup.key, cached);
            return cached;
        }
        return lookup.fallback;
    }

    private static Mesh flatten(ItemStack stack, BakedModel original) {
        if (original.isCustomRenderer()) return null;
        var pose = new PoseStack(); var model = ClientHooks.handleCameraTransforms(pose, original, ItemDisplayContext.GUI, false);
        if (model.isCustomRenderer()) return null;
        pose.translate(-.5, -.5, -.5);
        var quads = new ArrayList<float[]>(); var colors = new ArrayList<int[]>(); var random = RandomSource.create(42);
        int examined = 0;
        for (var pass : model.getRenderPasses(stack, true)) for (int face = 0; face <= DIRECTIONS.length; face++) {
            random.setSeed(42);
            for (var quad : pass.getQuads(null, face == DIRECTIONS.length ? null : DIRECTIONS[face], random)) {
                if (++examined > 512) return null;
                int[] data = quad.getVertices(); int stride = data.length / 4;
                if (stride < 6) return null;
                var positions = new Vector3f[4];
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * stride;
                    positions[vertex] = pose.last().pose().transformPosition(new Vector3f(Float.intBitsToFloat(data[offset]),
                            Float.intBitsToFloat(data[offset + 1]), Float.intBitsToFloat(data[offset + 2])));
                }
                var normal = new Vector3f(positions[1]).sub(positions[0]).cross(new Vector3f(positions[2]).sub(positions[0]));
                if (normal.z <= 1e-7f) continue;
                if (quads.size() >= MAX_QUADS) return null;
                float[] vertices = new float[20]; int[] tint = new int[4];
                int color = quad.isTinted() ? Minecraft.getInstance().getItemColors().getColor(stack, quad.getTintIndex()) : -1;
                float shade = model.isGui3d() && quad.isShade() ? faceShade(quad) : 1;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * stride, target = vertex * 5;
                    vertices[target] = positions[vertex].x; vertices[target + 1] = positions[vertex].y;
                    vertices[target + 2] = positions[vertex].z * .001f;
                    vertices[target + 3] = Float.intBitsToFloat(data[offset + 4]); vertices[target + 4] = Float.intBitsToFloat(data[offset + 5]);
                    tint[vertex] = tint(data[offset + 3], color, shade);
                }
                quads.add(vertices); colors.add(tint);
            }
        }
        if (quads.isEmpty()) return null;
        // This translucent batch does not write depth. Sort within each cached silhouette once;
        // equal-depth generated layers retain their original order.
        var order = new ArrayList<Integer>(); for (int i = 0; i < quads.size(); i++) order.add(i);
        order.sort(java.util.Comparator.comparingDouble(i -> {
            var quad = quads.get(i); return quad[2] + quad[7] + quad[12] + quad[17];
        }));
        float[] vertices = new float[quads.size() * 20]; int[] tint = new int[quads.size() * 4];
        for (int i = 0; i < quads.size(); i++) {
            int source = order.get(i);
            System.arraycopy(quads.get(source), 0, vertices, i * 20, 20); System.arraycopy(colors.get(source), 0, tint, i * 4, 4);
        }
        return new Mesh(vertices, tint);
    }

    private static float faceShade(BakedQuad quad) {
        return switch (quad.getDirection()) { case UP -> 1; case DOWN -> .5f; case NORTH, SOUTH -> .8f; case EAST, WEST -> .6f; };
    }

    private static int tint(int vertexAbgr, int tintArgb, float shade) {
        int alpha = (vertexAbgr >>> 24) * (tintArgb >>> 24) / 255;
        int red = (int) ((vertexAbgr & 255) * (tintArgb >> 16 & 255) / 255f * shade);
        int green = (int) ((vertexAbgr >> 8 & 255) * (tintArgb >> 8 & 255) / 255f * shade);
        int blue = (int) ((vertexAbgr >> 16 & 255) * (tintArgb & 255) / 255f * shade);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static Mesh fallback(ItemStack stack, BakedModel model) {
        var mc = Minecraft.getInstance();
        if (model.isCustomRenderer()) {
            // The mod's custom renderers expose their real artwork through these baked geometry models.
            if (stack.is(AstralContent.ASTRAL_GEM.get())) model = mc.getModelManager().getModel(AstralMineralClient.GEM_MODEL);
            else if (stack.is(AstralContent.ATTUNEMENT_WAND.get())) model = mc.getModelManager().getModel(AstralMineralClient.WAND_MODEL);
            else if (stack.is(AstralContent.ASTRAL_NEXUS.get())) model = mc.getModelManager().getModel(AstralMineralClient.REMOTE_MODEL);
            else if (stack.is(AstralContent.RESONANCE_GOGGLES.get())) model = mc.getModelManager().getModel(AstralMineralClient.GOGGLES_ICON_MODEL);
            else if (stack.getItem() instanceof BlockItem block && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("astral_repository"))
                model = mc.getModelManager().getModel(CrystalModelRenderer.modelLocation(block.getBlock()));
        }
        var sprite = model.getParticleIcon(); int color = mc.getItemColors().getColor(stack, 0);
        return new Mesh(new float[]{-.5f,-.5f,0,sprite.getU0(),sprite.getV1(), .5f,-.5f,0,sprite.getU1(),sprite.getV1(),
                .5f,.5f,0,sprite.getU1(),sprite.getV0(), -.5f,.5f,0,sprite.getU0(),sprite.getV0()}, new int[]{color,color,color,color});
    }

    private TransferItemSprites() { super("unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {}); }
}
