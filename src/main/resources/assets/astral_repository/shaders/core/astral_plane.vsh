#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <astral_repository:astral_parameters.glsl>
#ifndef ASTRAL_INTERFACE
#moj_import <minecraft:fog.glsl>
#endif

in vec3 Position;
in vec4 Color;
in vec2 UV0;

#ifndef ASTRAL_INTERFACE
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
#endif

out vec2 crystalUV;
out vec3 viewPosition;
out vec3 astralWorldPosition;
out vec3 astralWorldRay;
out vec4 surfaceColor;
out vec4 overlayColor;
out vec4 lightColor;
out float vertexDistance;
out float vertexCylindricalDistance;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    // Hand bob affects the visible model, but must not move the eye inside its material.
    viewPosition = (AstralParallaxViewCorrection * view).xyz;
    // World bob lives in ProjMat. Sample its forward-transformed eye ray so
    // the volume stays at the same screen pixels as bob-off, behind a bobbing window.
    // The real view rotation still rotates this ray into the shared world volume.
    vec3 relativeWorld = (AstralViewToWorld * view).xyz;
    astralWorldPosition = relativeWorld + AstralCameraPosition;
    astralWorldRay = (AstralViewToWorld * AstralWorldProjectionBob * view).xyz;
    crystalUV = UV0;
    surfaceColor = Color;
    #ifdef ASTRAL_INTERFACE
        // GUI panels do not bind world lightmaps or damage overlays.
        overlayColor = vec4(0.0);
        lightColor = vec4(1.0);
        vertexDistance = 0.0;
        vertexCylindricalDistance = 0.0;
    #else
        overlayColor = texelFetch(Sampler1, UV1, 0);
        lightColor = texelFetch(Sampler2, UV2 / 16, 0);
        vertexDistance = fog_spherical_distance(Position);
        vertexCylindricalDistance = fog_cylindrical_distance(Position);
    #endif
}
