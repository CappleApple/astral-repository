#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec2 maskCoord;
in float circle;
in vec4 vertexColor;
in float vertexDistance;
in float vertexCylindricalDistance;
out vec4 fragColor;
void main() {
    vec4 color=texture(Sampler0,texCoord)*vertexColor*ColorModulator;
    color.a*=mix(1.0,1.0-smoothstep(0.75,1.0,length(maskCoord)),circle);
    if(color.a<0.001)discard;
    fragColor=apply_fog(color,vertexDistance,vertexCylindricalDistance,FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,FogColor);
}
