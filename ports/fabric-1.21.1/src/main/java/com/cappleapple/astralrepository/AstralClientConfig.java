package com.cappleapple.astralrepository;

import io.github.fabricators_of_create.porting_lib.config.ModConfigSpec;

/** Local visual preferences. Registered only on the physical client. */
public final class AstralClientConfig {
    public static final double DEFAULT_ASTRAL_OVERLAY_OPACITY = 0.4;
    public static final double DEFAULT_ASTRAL_INTERFACE_OVERLAY_OPACITY = 0.24;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue astralOverlayOpacity = BUILDER
            .comment("Astral overlay opacity for natural crystals, gems, the large wand crystal, goggles lenses, and the remote orb. 0 keeps the base artwork; 1 shows the full purple depth field. Changes apply on the next material draw.")
            .defineInRange("astralOverlayOpacity", DEFAULT_ASTRAL_OVERLAY_OPACITY, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue astralInterfaceOverlayOpacity = BUILDER
            .comment("Astral field opacity behind Nexus and rune settings controls. Independent of world and item materials. 0 displays the original panel PNG; 1 displays the full field.")
            .defineInRange("astralInterfaceOverlayOpacity", DEFAULT_ASTRAL_INTERFACE_OVERLAY_OPACITY, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue astralShootingStars = BUILDER
            .comment("Occasional pixelated shooting stars inside astral world, item, and interface fields.")
            .define("astralShootingStars", true);
    public static final ModConfigSpec.DoubleValue astralLayerDriftSpeed = BUILDER
            .comment("Broad movement of the nebula and star layers. Speed multiplier: 0 stops this animation, 1 is normal, 10 is ten times faster.")
            .defineInRange("astralLayerDriftSpeed", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue astralLayerWobbleSpeed = BUILDER
            .comment("Small oscillations of the nebula and star layers. Speed multiplier: 0 stops this animation, 1 is normal, 10 is ten times faster.")
            .defineInRange("astralLayerWobbleSpeed", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue astralParticleSpeed = BUILDER
            .comment("Movement of individual square and rectangular stars. Speed multiplier: 0 stops this animation, 1 is normal, 10 is ten times faster.")
            .defineInRange("astralParticleSpeed", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue astralTwinkleSpeed = BUILDER
            .comment("Brightness pulsing of individual stars. Speed multiplier: 0 stops this animation, 1 is normal, 10 is ten times faster.")
            .defineInRange("astralTwinkleSpeed", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue astralShootingStarSpeed = BUILDER
            .comment("Shooting-star travel, fading, and event cadence; 0 suppresses shooting stars. Speed multiplier: 0 stops this animation, 1 is normal, 10 is ten times faster.")
            .defineInRange("astralShootingStarSpeed", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue transferPathVariation = BUILDER.comment("Maximum cosmetic sideways deviation in blocks for each resource batch. Variation stays active through relay crossings and fades only at the source and destination. Zero keeps identical paths. Resource landing positions use resourceArrivalVariance.").defineInRange("transferPathVariation", 0.2, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue resourceArrivalVariance = BUILDER.comment("Maximum distance in blocks from the destination center for each fluid, energy, or Source batch landing point. Zero lands at the center; sampled once per new flight. These sprites fade only inside the destination block.").defineInRange("resourceArrivalVariance", 0.18, 0.0, 0.45);
    public static final ModConfigSpec.IntValue maxActiveTransfers = BUILDER.comment("Maximum live transfer animations. New cosmetic flights are sampled out at this limit; admitted flights finish normally. Does not limit actual transfers.").defineInRange("maxActiveTransfers",4096,64,65536);
    public static final ModConfigSpec.IntValue maxItemTransferModels = BUILDER.comment("Maximum simultaneous full 3D moving item models. Additional item flights use batched flat icons; all media share maxActiveTransfers.").defineInRange("maxItemTransferModels",128,0,4096);
    public static final ModConfigSpec.IntValue maxResourceTrails = BUILDER.comment("Maximum detached resource trail sprites. Existing trails finish before new ones are admitted.").defineInRange("maxResourceTrails",512,0,8192);
    public static final ModConfigSpec.IntValue maxTransferTrailEmissions = BUILDER.comment("Maximum trail emissions per client tick before particleDensity is applied. Shared fairly across active flights.").defineInRange("maxTransferTrailEmissions",32,0,512);
    public static final ModConfigSpec.IntValue transferRenderDistance = BUILDER.comment("Maximum distance in blocks for transfer models, resource sprites, and their trails.").defineInRange("transferRenderDistance",96,16,256);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private AstralClientConfig() {}
}
