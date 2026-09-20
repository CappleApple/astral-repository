#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <astral_repository:astral_parameters.glsl>
#ifndef ASTRAL_INTERFACE
#include <minecraft:fog.glsl>
#include <minecraft:oit.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in vec2 crystalUV;
layout(location = 1) in vec3 viewPosition;
layout(location = 2) in vec3 astralWorldPosition;
layout(location = 3) in vec3 astralWorldRay;
layout(location = 4) in vec4 surfaceColor;
layout(location = 5) in vec4 overlayColor;
layout(location = 6) in vec4 lightColor;
layout(location = 7) in float vertexDistance;
layout(location = 8) in float vertexCylindricalDistance;
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// Repository-owned procedural field. No End sky or End portal texture is sampled.
float astralHash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.11369, 0.13787));
    q += dot(q, q.yzx + 19.19);
    return fract((q.x + q.y) * q.z);
}

float cloudHash(vec2 p, float period) {
    return astralHash(period > 0.0 ? mod(p, period) : p);
}

float cloudNoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(cloudHash(i, period), cloudHash(i + vec2(1.0, 0.0), period), f.x),
               mix(cloudHash(i + vec2(0.0, 1.0), period), cloudHash(i + 1.0, period), f.x), f.y);
}

vec2 rotateField(vec2 p, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c) * p;
}

// Build the portal's U/V frame from transformed geometry, not screen coordinates.
// Intersecting this ray with distinct interior planes makes distant details move farther.
vec2 interiorRaySlope(vec2 materialUV) {
    vec3 dx = dFdx(viewPosition);
    vec3 dy = dFdy(viewPosition);
    vec2 ux = dFdx(materialUV);
    vec2 uy = dFdy(materialUV);
    float determinant = ux.x * uy.y - ux.y * uy.x;
    if (abs(determinant) < 0.0000000001) return vec2(0.0);
    vec3 tangent = (dx * uy.y - dy * ux.y) * sign(determinant);
    vec3 bitangent = (dy * ux.x - dx * uy.x) * sign(determinant);
    float tangentSize = length(tangent);
    if (tangentSize < 0.0000001) return vec2(0.0);
    tangent /= tangentSize;
    bitangent -= tangent * dot(tangent, bitangent);
    float bitangentSize = length(bitangent);
    if (bitangentSize < 0.0000001) return vec2(0.0);
    bitangent /= bitangentSize;
    vec3 normal = normalize(cross(tangent, bitangent));
    // GUI item projections have parallel rays. Their position on the screen must not
    // change the interior; rotating the material still rotates its recovered frame.
    vec3 toEye = abs(ProjMat[3][3]) > 0.5 ? vec3(0.0, 0.0, 1.0)
        : -viewPosition / max(length(viewPosition), 0.00001);
    float facing = max(abs(dot(normal, toEye)), 0.22);
    return vec2(dot(tangent, toEye), dot(bitangent, toEye)) / facing;
}

// Measure coverage before cell wrapping and randomized sizes introduce discontinuities.
// Differentiating the final distance can turn a neighboring cell into a flashing halo.
float boxParticle(vec2 local, vec2 halfSize, vec2 footprint) {
    // Integrate the rectangle over a pixel footprint. Subpixel stars lose coverage
    // instead of retaining half-bright halos that pop as their cells change.
    vec2 pixel = max(footprint, vec2(0.003));
    vec2 overlap = max(vec2(0.0), min(local + pixel * 0.5, halfSize)
        - max(local - pixel * 0.5, -halfSize));
    vec2 coverage = clamp(overlap / pixel, 0.0, 1.0);
    return coverage.x * coverage.y;
}

// Independent world-axis planes share one volume across blocks and mesh facets.
// Camera coordinates wrap at 4096 blocks; periodic noise closes the same boundary.
vec2 worldInterior(int layer, vec3 entry, out float identity, out float visibility, out vec2 footprint) {
    int axis = layer % 3;
    vec3 ray = normalize(astralWorldRay);
    float velocity = ray[axis];
    visibility = smoothstep(0.08, 0.40, abs(velocity));
    float direction = velocity < 0.0 ? -1.0 : 1.0;
    float stepIndex = floor(float(layer) / 3.0) + 1.0;
    // Keep the entry surface outside the virtual volume. On exact four-block planes,
    // inverse-view rounding must not alternate between the surface and the next layer.
    float interiorEntry = entry[axis] + direction * 0.002;
    float plane = (floor(interiorEntry / 4.0)
        + (velocity > 0.0 ? stepIndex : 1.0 - stepIndex)) * 4.0;
    float distance = (plane - entry[axis]) / (direction * max(abs(velocity), 0.08));
    vec3 hit = entry + ray * distance;
    // Differentiate the selected plane analytically. Neighbor pixels can select a
    // different plane; differentiating hit/field across that jump creates false stars.
    vec3 px = dFdx(entry), py = dFdy(entry);
    vec3 rx = dFdx(ray), ry = dFdy(ray);
    float denominator = direction * max(abs(velocity), 0.08);
    float movingVelocity = abs(velocity) > 0.08 ? 1.0 : 0.0;
    vec3 hx = px + rx * distance - ray * (px[axis] + distance * rx[axis] * movingVelocity) / denominator;
    vec3 hy = py + ry * distance - ray * (py[axis] + distance * ry[axis] * movingVelocity) / denominator;
    vec2 sx = axis == 0 ? hx.yz : axis == 1 ? hx.xz : hx.xy;
    vec2 sy = axis == 0 ? hy.yz : axis == 1 ? hy.xz : hy.xy;
    footprint = (abs(sx) + abs(sy)) * 0.5;

    identity = 1.0 + float(axis) * 7.0 + astralHash(vec2(mod(plane / 4.0, 1024.0), float(axis) + 11.0)) * 17.0;
    vec2 surface = axis == 0 ? hit.yz : axis == 1 ? hit.xz : hit.xy;
    return surface * 0.5;
}

// One quick trajectory per event, drawn once after all depth layers. No tiled emitters.
vec3 shootingStar(bool worldMode, bool interfaceMode, vec2 materialUV) {
    float clock = AstralShootingStarTime * 50.0;
    float event = floor(clock);
    float age = fract(clock) * 24.0 - AstralMeteorStartTime;
    if (age <= 0.0 || age >= 0.70) return vec3(0.0);
    vec2 uv;
    if (worldMode) {
        vec3 ray = mat3(AstralMeteorWorldToView) * astralWorldRay;
        if (ray.z >= -0.0001) return vec3(0.0);
        float distance = (-64.0 - AstralMeteorEyeOffset.z) / ray.z;
        if (distance <= 1.0) return vec3(0.0);
        uv = (AstralMeteorEyeOffset + ray * distance).xy / vec2(80.0, 50.0) + 0.5;
    } else {
        uv = interfaceMode ? crystalUV : fract(materialUV);
    }
    float seed = astralHash(vec2(event, 9.3));
    float side = seed < 0.5 ? 1.0 : -1.0;
    vec2 start = vec2(side > 0.0 ? -0.12 : 1.12, 0.68);
    vec2 travel = vec2(side * 1.24, -0.30);
    vec2 direction = normalize(travel);
    vec2 head = floor((start + travel * (age / 0.65)) * 128.0) / 128.0;
    vec2 footprint = fwidth(uv);
    float core = boxParticle(uv - head, vec2(0.0075), footprint);
    float tail = 0.0;
    // Overlapping pixel steps form one tapered streak, rather than eight faint dots.
    for (int pixel = 1; pixel <= 24; pixel++) {
        float behind = float(pixel);
        vec2 point = floor((head - direction * behind / 128.0) * 128.0) / 128.0;
        float width = mix(0.0065, 0.0035, behind / 24.0);
        float brightness = pow(1.0 - behind / 25.0, 0.65);
        tail = max(tail, boxParticle(uv - point, vec2(width), footprint) * brightness);
    }
    float fade = smoothstep(0.0, 0.04, age) * (1.0 - smoothstep(0.55, 0.70, age));
    return fade * (core * vec3(1.6, 1.7, 2.0) + tail * vec3(1.0, 0.72, 1.6));
}

void main() {
    // The crystal is the window's fixed entry plane. Source texels remain fixed while
    // each layer samples the point where the eye ray meets a virtual plane behind it.
    bool interfaceMode = AstralInterfaceMode > 0.5;
    bool worldMode = AstralWorldMode > 0.5 && !interfaceMode;
    vec3 entry = astralWorldPosition;
    if (worldMode) {
        // The block window still bobs. Intersect the bob-free screen ray with its
        // original surface plane so bob cannot change the chosen interior layer.
        vec3 normal = cross(dFdx(astralWorldPosition), dFdy(astralWorldPosition));
        float facing = dot(normal, astralWorldRay);
        if (abs(facing) > 0.00000001) {
            float distance = dot(normal, astralWorldPosition - AstralCameraPosition) / facing;
            entry = AstralCameraPosition + astralWorldRay * distance;
        }
    }
    // GUI pixels use a larger field scale so stars remain visible behind the controls.
    vec2 materialUV = crystalUV * vec2(textureSize(Sampler0, 0)) / (interfaceMode ? 64.0 : 16.0);
    if(AstralItemMode>0.5)materialUV=(crystalUV-AstralSpriteBounds.xy)/max(AstralSpriteBounds.zw,vec2(0.000001));
    vec2 raySlope = interfaceMode || worldMode ? vec2(0.0) : interiorRaySlope(materialUV);
    vec4 mineral = texture(Sampler0, crystalUV);
    if (mineral.a < 0.1) discard;
    float cycle = AstralLayerDriftTime * 6.28318530718;
    float wobbleCycle = AstralLayerWobbleTime * 6.28318530718 * 3.0;
    float particleCycle = AstralParticleTime * 6.28318530718 * 3.0;
    float twinkleCycle = AstralTwinkleTime * 6.28318530718 * 3.0;
    float opacity = clamp(AstralOverlayOpacity, 0.0, 1.0);
    vec3 veil = vec3(0.065, 0.015, 0.16);
    vec3 starlight = vec3(0.0);
    for (int layer = 0; layer < 7 && opacity > 0.0; layer++) {
        float index = float(layer);
        float depth = 0.14 + index * 0.12 + index * index * 0.043;
        float seedDepth = index + 1.0;
        float visibility = 1.0;
        vec2 interior = materialUV - raySlope * depth;
        vec2 worldFootprint = vec2(0.0);
        if (worldMode) interior = worldInterior(layer, entry, seedDepth, visibility, worldFootprint);
        float layerPhase = astralHash(vec2(seedDepth, 31.7)) * 6.28318530718;
        float driftRateX = 3.0 + floor(astralHash(vec2(seedDepth, 7.3)) * 7.0);
        float driftRateY = 4.0 + floor(astralHash(vec2(seedDepth, 17.9)) * 8.0);
        vec2 field = interior * (worldMode ? 1.0 : 0.72 + index * 0.045) + vec2(seedDepth * 17.3, seedDepth * 9.7);
        // Independent normalized clocks retain fractional-speed continuity across daily wraps.
        field += vec2(sin(wobbleCycle * driftRateX + layerPhase), cos(wobbleCycle * driftRateY + layerPhase * 1.37)) * 0.025;
        // Slow, wider translation carries entire layers past the fixed surface window.
        field += vec2(sin(cycle * 7.0 + layerPhase), cos(cycle * 5.0 + layerPhase * 1.37)) * (worldMode ? 0.16 : 0.10 + index * 0.03);
        vec2 cloudField = worldMode ? field : rotateField(field, seedDepth * 1.71);
        float cloud = cloudNoise(cloudField * 3.0 + seedDepth * 13.7, worldMode ? 6144.0 : 0.0);
        cloud += cloudNoise(cloudField * 7.0 - seedDepth * 8.3, worldMode ? 14336.0 : 0.0) * 0.4;
        veil += mix(vec3(0.075, 0.008, 0.15), vec3(0.19, 0.035, 0.27), cloud) * visibility / (worldMode ? 5.0 : index + 3.0);

        float frequency = worldMode ? 10.0 + float(layer % 3) * 2.0 : 10.0 + index * 1.7;
        vec2 cells = field * frequency;
        vec2 cell = worldMode ? mod(floor(cells), 2048.0 * frequency) : floor(cells);
        vec2 identity = cell + seedDepth * 53.1;
        float seed = astralHash(identity);
        float phase = astralHash(identity + 29.4) * 6.28318530718;
        float orbitRateX = 12.0 + floor(astralHash(identity + 47.3) * 17.0);
        float orbitRateY = 15.0 + floor(astralHash(identity + 63.8) * 19.0);
        vec2 center = vec2(0.34 + astralHash(identity + 2.1) * 0.32, 0.34 + astralHash(identity + 7.8) * 0.32);
        center += vec2(sin(particleCycle * orbitRateX + phase), cos(particleCycle * orbitRateY + phase * 1.61)) * 0.035;
        float size = mix(0.07, 0.145, astralHash(identity + 11.3));
        float shape = astralHash(identity + 93.2);
        vec2 proportions = shape < 0.38 ? vec2(1.0)
            : shape < 0.69 ? vec2(1.9, 0.55) : vec2(0.55, 1.9);
        float star = boxParticle(fract(cells) - center, proportions * size, worldMode ? worldFootprint * frequency : fwidth(cells)) * step(0.985, seed);
        float twinkleRate = 19.0 + floor(astralHash(identity + 81.7) * 25.0);
        float pulse = 0.78 + 0.14 * sin(twinkleCycle * twinkleRate + phase) + 0.08 * sin(twinkleCycle * (twinkleRate + 7.0) + phase * 1.37);
        starlight += star * pulse * mix(vec3(0.60, 0.27, 1.0), vec3(1.0, 0.83, 1.0), seed) * (worldMode ? 0.82 : 1.05 - (index + 1.0) * 0.055) * visibility;
    }
    if (opacity > 0.0 && AstralShootingStars > 0.5) starlight += shootingStar(worldMode, interfaceMode, materialUV);
    // Shift the original texels toward saturated blue without dimming their value.
    // Bright facets stay pale; dark facets and budding cracks keep their original contrast.
    float value = max(mineral.r, max(mineral.g, mineral.b));
    float low = min(mineral.r, min(mineral.g, mineral.b));
    float saturation = clamp((value - low) / max(value, 0.0001) * 1.2, 0.0, 1.0);
    vec3 blueMineral = mix(vec3(value), vec3(0.05, 0.32, 1.0) * value, saturation);
    // One opacity controls the entire field, including every star. Zero is the clean base.
    // Custom wand artwork keeps its existing blue; natural amethyst uses the blue retint.
    vec3 base = mix(mineral.rgb, blueMineral, clamp(AstralRetintBase, 0.0, 1.0));
    vec3 color = mix(base, veil + starlight, opacity);
    if (!interfaceMode) {
        color *= mix(vec3(0.65), lightColor.rgb, 0.35);
        color = mix(overlayColor.rgb, color, overlayColor.a);
    }
    vec4 shaded = vec4(color, mineral.a) * surfaceColor * ColorModulator;
    #ifdef ASTRAL_INTERFACE
    fragColor = shaded;
    #else
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z,shaded.a);
    #else
    #ifdef OIT_ACCUMULATE
    shaded=sampleColorForAccumulation(shaded);
    vec4 fogColor=vec4(FogColor.rgb*shaded.a,FogColor.a);
    #else
    vec4 fogColor=FogColor;
    #endif
    fragColor = apply_fog(shaded, vertexDistance, vertexCylindricalDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
    #endif
}


