uniform float VeilRenderTime;

in vec4 vertexColor;
in vec3 effectNormal;
in vec3 viewNormal;
in float pulse;

out vec4 fragColor;

void main() {
    vec3 n = normalize(effectNormal);
    float fresnel = pow(1.0 - clamp(abs(viewNormal.z), 0.0, 1.0), 2.1);
    float latitude = 0.5 + 0.5 * sin(n.y * 31.0 - VeilRenderTime * 3.4);
    float longitude = 0.5 + 0.5 * sin(atan(n.z, n.x) * 12.0 + VeilRenderTime * 1.8);
    float dataLines = smoothstep(0.78, 1.0, latitude * longitude);
    float energy = 0.72 + fresnel * 0.34 + pulse * 0.06 + dataLines * 0.16;
    // Hue-preserving highlight instead of fixed silver, keeps tier colors saturated
    vec3 highlight = mix(vertexColor.rgb, vec3(1.0), 0.55);
    vec3 surfaceColor = mix(vertexColor.rgb, highlight, fresnel * 0.30 + dataLines * 0.22);
    fragColor = vec4(surfaceColor * energy, vertexColor.a);
}
