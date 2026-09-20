#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <astral_repository:astral_parameters.glsl>
#ifndef ASTRAL_INTERFACE
#include <minecraft:fog.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;

#ifndef ASTRAL_INTERFACE
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
#endif

layout(location = 0) out vec2 crystalUV;
layout(location = 1) out vec3 viewPosition;
layout(location = 2) out vec3 astralWorldPosition;
layout(location = 3) out vec3 astralWorldRay;
layout(location = 4) out vec4 surfaceColor;
layout(location = 5) out vec4 overlayColor;
layout(location = 6) out vec4 lightColor;
layout(location = 7) out float vertexDistance;
layout(location = 8) out float vertexCylindricalDistance;

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
