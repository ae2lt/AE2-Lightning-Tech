uniform float VeilRenderTime;

in vec4 vertexColor;
in vec3 effectNormal;
in vec3 effectPosition;
in vec3 viewNormal;
in float pulse;

out vec4 fragColor;

void main() {
    vec3 n = normalize(effectNormal);
    float fresnel = pow(1.0 - clamp(abs(viewNormal.z), 0.0, 1.0), 2.2);
    float flow = sin(
            effectPosition.x * 15.0 +
            effectPosition.y * 11.0 -
            effectPosition.z * 13.0 +
            sin(effectPosition.y * 7.0 + VeilRenderTime * 1.6) * 2.2);
    float fissure = 1.0 - smoothstep(0.0, 0.14, abs(flow));
    float luminance = dot(vertexColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    float darkCore = 1.0 - smoothstep(0.08, 0.32, luminance);
    vec3 hue = normalize(max(vertexColor.rgb, vec3(0.01)));
    vec3 baseColor = vertexColor.rgb * (0.48 + pulse * 0.05 + fresnel * 0.12);
    vec3 fissureColor = mix(hue, vec3(0.82, 0.90, 0.94), 0.28);
    float fissureStrength = fissure * (0.08 + darkCore * 0.46);
    fragColor = vec4(baseColor + fissureColor * fissureStrength, vertexColor.a);
}
