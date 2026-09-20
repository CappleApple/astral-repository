#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec2 maskCoord;
layout(location = 2) out float circle;
layout(location = 3) out vec4 vertexColor;
layout(location = 4) out float vertexDistance;
layout(location = 5) out float vertexCylindricalDistance;
void main() {
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    texCoord=UV0;
    maskCoord=vec2(UV1)/32767.0*2.0-1.0;
    circle=UV1.x<0?0.0:1.0;
    vertexColor=Color;
    vertexDistance=fog_spherical_distance(Position);
    vertexCylindricalDistance=fog_cylindrical_distance(Position);
}
