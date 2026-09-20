#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec2 texCoord;
in vec2 maskCoord;
in float circle;
in vec4 vertexColor;
in float vertexDistance;
out vec4 fragColor;
void main() {
    vec4 color=texture(Sampler0,texCoord)*vertexColor*ColorModulator;
    color.a*=mix(1.0,1.0-smoothstep(0.75,1.0,length(maskCoord)),circle);
    if(color.a<0.001)discard;
    fragColor=linear_fog(color,vertexDistance,FogStart,FogEnd,FogColor);
}
