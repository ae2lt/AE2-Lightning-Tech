layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// Veil 1.20 exposes vanilla GameTime (one unit per Minecraft day) rather than
// VeilRenderTime. Convert it to seconds so this backend matches the native animation speed.
uniform float GameTime;
#define VeilRenderTime (GameTime * 1200.0)

out vec4 vertexColor;
out vec3 effectNormal;
out vec3 effectPosition;
out vec3 viewNormal;
out float pulse;

void main() {
    vec3 normal = normalize(Normal);
    float phase = dot(normal, vec3(5.3, 7.1, 9.7)) + VeilRenderTime * 2.2;
    pulse = 0.5 + 0.5 * sin(phase);

    float displacement = sin(phase * 1.7) * 0.012 * Color.a;
    vec3 displacedPosition = Position + normal * displacement;
    gl_Position = ProjMat * ModelViewMat * vec4(displacedPosition, 1.0);

    vertexColor = Color;
    effectNormal = normal;
    effectPosition = Position;
    viewNormal = normalize(mat3(ModelViewMat) * normal);
}
