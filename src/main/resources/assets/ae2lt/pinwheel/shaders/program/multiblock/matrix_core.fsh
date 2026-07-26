uniform float VeilRenderTime;

in vec4 vertexColor;
in vec3 effectNormal;
in vec3 effectPosition;
in vec3 viewNormal;
in float pulse;

out vec4 fragColor;

void main() {
    vec3 n = normalize(effectNormal);
    float fresnel = pow(1.0 - clamp(abs(viewNormal.z), 0.0, 1.0), 2.4);
    float flow = 0.5 + 0.5 * sin(
            effectPosition.x * 9.0 +
            effectPosition.y * 13.0 -
            effectPosition.z * 7.0 -
            n.y * 2.0 -
            VeilRenderTime * 1.5);
    float trace = smoothstep(0.90, 1.0, flow);
    float fissureFlow = sin(
            effectPosition.x * 15.0 +
            effectPosition.y * 11.0 -
            effectPosition.z * 13.0 +
            sin(effectPosition.y * 7.0 + VeilRenderTime * 1.6) * 2.2);
    float fissure = 1.0 - smoothstep(0.0, 0.14, abs(fissureFlow));
    float coreMask = smoothstep(0.95, 0.97, vertexColor.a);
    vec3 hue = normalize(max(vertexColor.rgb, vec3(0.01)));
    float baseEnergy = mix(0.74, 0.58, coreMask);
    vec3 baseColor = vertexColor.rgb * (baseEnergy + pulse * 0.035 + fresnel * 0.14);
    vec3 traceColor = mix(hue, vec3(0.82, 0.90, 0.94), 0.22);
    vec3 fissureColor = mix(hue, vec3(0.82, 0.90, 0.94), 0.28);
    vec3 color = baseColor
            + traceColor * trace * 0.075 * (1.0 - coreMask)
            + fissureColor * fissure * 0.46 * coreMask;
    fragColor = vec4(color, vertexColor.a);
}
