#version 150
#moj_import <fog.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
out vec2 texCoord;
out vec2 maskCoord;
out float circle;
out vec4 vertexColor;
out float vertexDistance;
void main() {
    gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
    texCoord=UV0;
    maskCoord=vec2(UV1)/32767.0*2.0-1.0;
    circle=UV1.x<0?0.0:1.0;
    vertexColor=Color;
    vertexDistance=fog_distance(ModelViewMat,Position,FogShape);
}

