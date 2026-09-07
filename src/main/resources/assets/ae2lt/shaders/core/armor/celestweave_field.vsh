#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
out vec2 fieldUV;
out vec3 viewPosition;
out vec3 viewNormal;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexColor = Color;
    fieldUV = UV0;
    viewPosition = view.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
}
