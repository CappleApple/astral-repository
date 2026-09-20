#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform int FogShape;
uniform float AstralInterfaceMode;
uniform mat4 AstralViewToWorld;
uniform mat4 AstralParallaxViewCorrection;
uniform mat4 AstralWorldProjectionBob;
uniform vec3 AstralCameraPosition;

out vec2 crystalUV;
out vec3 viewPosition;
out vec3 astralWorldPosition;
out vec3 astralWorldRay;
out vec4 surfaceColor;
out vec4 overlayColor;
out vec4 lightColor;
out float vertexDistance;

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
    if (AstralInterfaceMode > 0.5) {
        // GUI panels do not bind world lightmaps or damage overlays.
        overlayColor = vec4(0.0);
        lightColor = vec4(1.0);
        vertexDistance = 0.0;
    } else {
        overlayColor = texelFetch(Sampler1, UV1, 0);
        lightColor = texelFetch(Sampler2, UV2 / 16, 0);
        vertexDistance = fog_distance(Position, FogShape);
    }
}
