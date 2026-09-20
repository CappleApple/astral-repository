layout(std140) uniform AstralParameters {
    mat4 AstralViewToWorld;
    mat4 AstralParallaxViewCorrection;
    mat4 AstralWorldProjectionBob;
    mat4 AstralMeteorWorldToView;
    vec4 AstralCameraData;
    vec4 AstralMeteorOffsetData;
    vec4 AstralTimes1;
    vec4 AstralTimes2;
    vec4 AstralModes;
    vec4 AstralSpriteBounds;
};
#define AstralCameraPosition AstralCameraData.xyz
#define AstralMeteorEyeOffset AstralMeteorOffsetData.xyz
#define AstralLayerDriftTime AstralTimes1.x
#define AstralLayerWobbleTime AstralTimes1.y
#define AstralParticleTime AstralTimes1.z
#define AstralTwinkleTime AstralTimes1.w
#define AstralShootingStarTime AstralTimes2.x
#define AstralMeteorStartTime AstralTimes2.y
#define AstralOverlayOpacity AstralTimes2.z
#define AstralRetintBase AstralTimes2.w
#define AstralItemMode AstralModes.x
#define AstralInterfaceMode AstralModes.y
#define AstralWorldMode AstralModes.z
#define AstralShootingStars AstralModes.w
