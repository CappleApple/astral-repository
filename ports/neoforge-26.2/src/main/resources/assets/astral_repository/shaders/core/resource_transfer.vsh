#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
out vec2 texCoord;
out vec2 maskCoord;
out float circle;
out vec4 vertexColor;
out float vertexDistance;
out float vertexCylindricalDistance;
void main() {
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    texCoord=UV0;
    maskCoord=vec2(UV1)/32767.0*2.0-1.0;
    circle=UV1.x<0?0.0:1.0;
    vertexColor=Color;
    vertexDistance=fog_spherical_distance(Position);
    vertexCylindricalDistance=fog_cylindrical_distance(Position);
}
