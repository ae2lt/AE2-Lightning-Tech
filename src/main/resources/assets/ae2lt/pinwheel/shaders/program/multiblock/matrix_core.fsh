uniform float GameTime;
#define VeilRenderTime (GameTime * 1200.0)

in vec4 vertexColor;
in vec3 effectNormal;
in vec3 effectPosition;
in vec3 viewNormal;
in float pulse;

out vec4 fragColor;

void main() {
    vec3 n = normalize(effectNormal);
    float fresnel = pow(1.0 - clamp(abs(viewNormal.z), 0.0, 1.0), 2.4);
    float flowA = 0.5 + 0.5 * sin(
            effectPosition.x * 12.0 +
            effectPosition.y * 17.0 -
            effectPosition.z * 9.0 -
            VeilRenderTime * 2.4);
    float flowB = 0.5 + 0.5 * sin(
            effectPosition.x * 5.0 -
            effectPosition.y * 8.0 +
            effectPosition.z * 14.0 +
            VeilRenderTime * 1.3);
    float trace = smoothstep(0.84, 1.0, flowA * 0.72 + flowB * 0.28);
    float fissureFlow = sin(
            effectPosition.x * 15.0 +
            effectPosition.y * 11.0 -
            effectPosition.z * 13.0 +
            sin(effectPosition.y * 7.0 + VeilRenderTime * 1.6) * 2.2);
    float fissure = 1.0 - smoothstep(0.0, 0.14, abs(fissureFlow));
    float coreMask = smoothstep(0.95, 0.97, vertexColor.a);
    vec3 hue = normalize(max(vertexColor.rgb, vec3(0.01)));
    vec3 coldWhite = vec3(0.84, 0.92, 1.0);
    vec3 traceColor = mix(hue, coldWhite, 0.34);
    vec3 fissureColor = mix(hue, vec3(0.82, 0.90, 0.94), 0.28);

    vec3 coreColor = vertexColor.rgb * (0.52 + pulse * 0.025)
            + hue * fresnel * 0.055
            + fissureColor * fissure * 0.44;

    float glowEnergy = 0.64
            + fresnel * 0.72
            + pulse * 0.10
            + trace * 0.62;
    vec3 glowColor = vertexColor.rgb * glowEnergy
            + traceColor * trace * 0.42
            + coldWhite * fresnel * 0.12;
    float glowAlpha = vertexColor.a * (0.76 + flowA * 0.16 + trace * 0.08);

    fragColor = vec4(
            mix(glowColor, coreColor, coreMask),
            mix(glowAlpha, vertexColor.a, coreMask));
}
