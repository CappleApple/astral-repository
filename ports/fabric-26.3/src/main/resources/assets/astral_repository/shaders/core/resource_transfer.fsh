#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 maskCoord;
layout(location = 2) in float circle;
layout(location = 3) in vec4 vertexColor;
layout(location = 4) in float vertexDistance;
layout(location = 5) in float vertexCylindricalDistance;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 color=texture(Sampler0,texCoord)*vertexColor*ColorModulator;
    color.a*=mix(1.0,1.0-smoothstep(0.75,1.0,length(maskCoord)),circle);
    if(color.a<0.001)discard;
    fragColor=apply_fog(color,vertexDistance,vertexCylindricalDistance,FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,FogColor);
}
