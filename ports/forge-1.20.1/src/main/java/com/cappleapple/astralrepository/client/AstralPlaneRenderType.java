package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.cappleapple.astralrepository.AstralClientConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Culled astral materials for natural minerals and already-colored custom artwork. */
public final class AstralPlaneRenderType extends RenderType {
    private static ShaderInstance shader;
    private static boolean worldSpace;
    private static net.minecraft.world.phys.Vec3 worldEye = net.minecraft.world.phys.Vec3.ZERO;
    private static net.minecraft.world.phys.Vec3 meteorEye = net.minecraft.world.phys.Vec3.ZERO;
    private static final org.joml.Matrix4f meteorWorldToView = new org.joml.Matrix4f();
    private static final org.joml.Vector3f meteorEyeOffset = new org.joml.Vector3f();
    private static Object meteorLevel;
    private static int meteorEvent = -1;
    private static float previousMeteorTime = -1;

    private static final org.joml.Matrix4f viewToWorld = new org.joml.Matrix4f();
    private static final org.joml.Vector3f cameraPosition = new org.joml.Vector3f();
    private static float appliedOverlayOpacity = Float.NaN;
    private static float appliedInterfaceOverlayOpacity = Float.NaN;
    private static boolean appliedInterfaceMode;
    private static final Map<ResourceLocation, RenderType> INTERFACES = new HashMap<>();
    public static final RenderType ASTRAL_PLANE = material("astral_repository:astral_plane", false, true);
    public static final RenderType ASTRAL_OVERLAY = material("astral_repository:astral_overlay", false, true);

    public static final RenderType GOGGLES_LENS = create("astral_repository:goggles_lens",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shaderForDraw(false, false)))
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                    .setCullState(CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .createCompositeState(false));

    public static final RenderType GEM_ITEM=create("astral_repository:gem_item",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->{
                var result=shaderForDraw(false,false);
                if(result!=null){var sprite=net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(AstralMineralClient.GEM_MODEL).getParticleIcon();
                    result.safeGetUniform("AstralWorldMode").set(0F);result.safeGetUniform("AstralItemMode").set(1F);
                    result.safeGetUniform("AstralSpriteBounds").set(sprite.getU0(),sprite.getV0(),sprite.getU1()-sprite.getU0(),sprite.getV1()-sprite.getV0());}
                return result;
            })).setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS,false,false)).setCullState(CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));
    public static final RenderType ITEM_TRIM = create("astral_repository:item_trim",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shaderForDraw(false, false)))
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setCullState(CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .createCompositeState(false));

    private static final RenderType ARMOR_TRIM = armorTrimMaterial(false);
    private static final RenderType ARMOR_TRIM_DECAL = armorTrimMaterial(true);

    public static RenderType armorTrim(boolean decal) { return decal ? ARMOR_TRIM_DECAL : ARMOR_TRIM; }
    private static RenderType armorTrimMaterial(boolean decal) {
        return create("astral_repository:armor_trim" + (decal ? "_decal" : ""),
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shaderForDraw(false, false)))
                        .setTextureState(new TextureStateShard(net.minecraft.client.renderer.Sheets.ARMOR_TRIMS_SHEET, false, false))
                        .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setDepthTestState(decal ? EQUAL_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                        .createCompositeState(true));
    }

    private static RenderType material(String name, boolean retintBase, boolean affectsCrumbling) {
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, affectsCrumbling, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shaderForDraw(retintBase, false)))
                        .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setCullState(CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                        .createCompositeState(true));
    }
    static RenderType interfaceMaterial(ResourceLocation texture) {
        return INTERFACES.computeIfAbsent(texture, key -> create("astral_repository:interface/" + key,
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shaderForDraw(false, true)))
                        .setTextureState(new TextureStateShard(key, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }
    private static ShaderInstance shaderForDraw(boolean retintBase, boolean interfaceMode) {
        if (shader != null) {
            shader.safeGetUniform("AstralItemMode").set(0F);
            float opacity = (interfaceMode ? AstralClientConfig.astralInterfaceOverlayOpacity
                    : AstralClientConfig.astralOverlayOpacity).get().floatValue();
            shader.safeGetUniform("AstralOverlayOpacity").set(opacity);
            shader.safeGetUniform("AstralRetintBase").set(retintBase ? 1.0F : 0.0F);
            shader.safeGetUniform("AstralInterfaceMode").set(interfaceMode ? 1.0F : 0.0F);
            shader.safeGetUniform("AstralShootingStars").set(AstralClientConfig.astralShootingStars.get() ? 1.0F : 0.0F);
            var level = net.minecraft.client.Minecraft.getInstance().level;
            long worldTicks = level == null ? 0 : level.getGameTime();
            float dayFraction = com.mojang.blaze3d.systems.RenderSystem.getShaderGameTime();
            shader.safeGetUniform("AstralLayerDriftTime").set(AstralAnimationClock.phase(worldTicks, dayFraction, AstralClientConfig.astralLayerDriftSpeed.get()));
            shader.safeGetUniform("AstralLayerWobbleTime").set(AstralAnimationClock.phase(worldTicks, dayFraction, AstralClientConfig.astralLayerWobbleSpeed.get()));
            shader.safeGetUniform("AstralParticleTime").set(AstralAnimationClock.phase(worldTicks, dayFraction, AstralClientConfig.astralParticleSpeed.get()));
            shader.safeGetUniform("AstralTwinkleTime").set(AstralAnimationClock.phase(worldTicks, dayFraction, AstralClientConfig.astralTwinkleSpeed.get()));
            float meteorTime = AstralAnimationClock.phase(worldTicks, dayFraction, AstralClientConfig.astralShootingStarSpeed.get());
            shader.safeGetUniform("AstralShootingStarTime").set(meteorTime);
            updateMeteor(meteorTime, level, worldSpace && !interfaceMode);
            shader.safeGetUniform("AstralWorldMode").set(worldSpace && !interfaceMode ? 1.0F : 0.0F);
            shader.safeGetUniform("AstralViewToWorld").set(viewToWorld);
            shader.safeGetUniform("AstralParallaxViewCorrection").set(AstralViewBobbing.correction());
            shader.safeGetUniform("AstralWorldProjectionBob").set(AstralViewBobbing.worldProjectionBob());
            shader.safeGetUniform("AstralCameraPosition").set(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            appliedInterfaceMode = interfaceMode;
            if (interfaceMode) appliedInterfaceOverlayOpacity = opacity;
            else appliedOverlayOpacity = opacity;
        }
        return shader;
    }
    // One track is shared by every world material draw. Once a streak starts its frame
    // stays fixed in world space, including while the player moves or turns.
    private static void updateMeteor(float time, Object level, boolean worldDraw) {
        float clock = time * 50;
        int event = (int)Math.floor(clock);
        int hash = (event + 1) * 0x45d9f3b;
        hash ^= hash >>> 16;
        float start = 4 + (hash & 65535) / 65535F * 15;
        float age = (clock - event) * 24 - start;
        if (worldDraw) {
            if (event != meteorEvent || meteorLevel != level || time < previousMeteorTime || age < 0) {
                meteorEye = worldEye;
                meteorWorldToView.set(viewToWorld).invert();
                meteorEvent = event;
                meteorLevel = level;
            }
            previousMeteorTime = time;
            meteorEyeOffset.set((float)(worldEye.x - meteorEye.x), (float)(worldEye.y - meteorEye.y), (float)(worldEye.z - meteorEye.z));
            meteorWorldToView.transformDirection(meteorEyeOffset);
        }
        shader.safeGetUniform("AstralMeteorStartTime").set(start);
        shader.safeGetUniform("AstralMeteorWorldToView").set(meteorWorldToView);
        shader.safeGetUniform("AstralMeteorEyeOffset").set(meteorEyeOffset.x, meteorEyeOffset.y, meteorEyeOffset.z);
    }

    /** Called at world-render boundaries, before any shared material buffers are drawn. */
    public static void beginWorld(org.joml.Matrix4f worldView, net.minecraft.world.phys.Vec3 camera) {
        worldSpace = true;
        worldEye = camera;
        viewToWorld.set(worldView).invert();
        cameraPosition.set(wrap(camera.x), wrap(camera.y), wrap(camera.z));
    }
    static boolean setWorldSpace(boolean enabled){boolean previous=worldSpace;worldSpace=enabled;return previous;}
    public static void endWorld() { worldSpace = false; viewToWorld.identity(); cameraPosition.zero(); }
    private static float wrap(double coordinate) { return (float)(coordinate - Math.floor(coordinate / 4096.0) * 4096.0); }

    /** Last value written while setting up an actual material draw, for client diagnostics. */
    public static float appliedOverlayOpacity() { return appliedOverlayOpacity; }
    public static float appliedInterfaceOverlayOpacity() { return appliedInterfaceOverlayOpacity; }
    public static boolean appliedInterfaceMode() { return appliedInterfaceMode; }
    public static void loaded(ShaderInstance loaded) { shader = loaded; }
    public static boolean ready() { return shader != null; }
    private AstralPlaneRenderType() { super("unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {}); }
}
