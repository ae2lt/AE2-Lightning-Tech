#version 150
uniform vec4 ColorModulator;
uniform float GameTime;
in vec4 vertexColor;
in vec2 fieldUV;
in vec3 viewPosition;
in vec3 viewNormal;
out vec4 fragColor;
float hash(float n) { return fract(sin(n * 127.1 + 311.7) * 43758.5453); }
float fracture(float y, float seed) {
    float p = y * 17.0;
    return mix(hash(floor(p) + seed), hash(floor(p) + seed + 1.0), fract(p)) * 2.0 - 1.0;
}
void main() {
    float time = GameTime * 24000.0;
    float family = floor(fieldUV.y / 4.0);
    float localV = fieldUV.y - family * 4.0;
    float outer = step(1.5, localV);
    vec2 rawUV = vec2(fieldUV.x, localV - outer * 2.0);
    // Quantized pattern coordinates match the game's pixels instead of smooth filaments.
    vec2 grid = family > 2.5 && family < 3.5 ? vec2(32.0, 8.0) : vec2(24.0, 32.0);
    vec2 uv = (floor(rawUV * grid) + 0.5) / grid;
    float x = abs(uv.x * 2.0 - 1.0);
    // Broad straight sides with small stepped corner cutouts, not an oval field petal.
    float width = uv.y < 0.0625 || uv.y > 0.9375 ? 0.75 : 0.94;
    float distance = width - x;
    float shape = smoothstep(-0.015, 0.035, distance);
    float edge = exp(-pow((distance - 0.035) / 0.065, 2.0));
    float ends = smoothstep(0.0, 0.032, rawUV.y) * (1.0 - smoothstep(0.968, 1.0, rawUV.y));
    float facing = abs(dot(normalize(viewNormal), normalize(-viewPosition)));
    float rim = pow(1.0 - facing, 1.6);
    // Pixel-aligned lightning trunks and forks weave across each flat armor face.
    float bolt = 0.0;
    float halo = 0.0;
    float aa = max(fwidth(rawUV.x), 0.003);
    for (int i = 0; i < 3; i++) {
        float seed = float(i) * 13.0 + outer * 47.0 + family * 19.0;
        float trunk = 0.22 + float(i) * 0.28 + fracture(uv.y, seed) * 0.048;
        trunk += sin(uv.y * 4.0 + seed) * 0.035;
        trunk = (floor(trunk * 24.0) + 0.5) / 24.0;
        float dist = abs(uv.x - trunk);
        float pulse = 0.63 + 0.37 * pow(max(0.0, sin(uv.y * 8.0 - time * 0.14 + seed)), 6.0);
        bolt += (1.0 - smoothstep(0.009, 0.021 + aa, dist)) * pulse;
        halo += exp(-dist * 62.0) * pulse;
        for (int j = 0; j < 2; j++) {
            float start = 0.20 + float(j) * 0.38 + hash(seed) * 0.1;
            float reach = uv.y - start;
            float branch = trunk + (mod(float(i+j), 2.0) * 2.0 - 1.0) * reach * 0.62
                    + fracture(uv.y, seed + 7.0) * 0.022;
            float branchMask = smoothstep(0.0, 0.035, reach) * (1.0 - smoothstep(0.13, 0.25, reach));
            branch = (floor(branch * 24.0) + 0.5) / 24.0;
            float branchDist = abs(uv.x - branch);
            bolt += (1.0 - smoothstep(0.005, 0.016 + aa, branchDist)) * branchMask * pulse * 0.75;
            halo += exp(-branchDist * 80.0) * branchMask * 0.35;
        }
    }
    // Broken stepped chevrons and bright corner stitches give the limbs secondary motifs.
    float motifY = family < 4.5 ? 0.23 : family < 5.5 ? 0.55 : family < 6.5 ? 0.31 : 0.70;
    float chevron = abs(uv.y - (motifY + abs(uv.x - 0.5) * 0.38));
    float ornament = (1.0 - smoothstep(0.014, 0.034, chevron))
            * step(0.13, abs(uv.x - 0.5)) * 0.65;
    float hem = (1.0 - smoothstep(0.016, 0.045, abs(uv.y - 0.84)))
            * step(0.29, abs(uv.x - 0.5));
    bolt = max(bolt, max(ornament, hem * 0.75));
    float embroidery = 0.0;
    if (family > 0.5 && family < 3.5) {
        float dist;
        if (family < 1.5) {
            // Shoulder-to-heart forks plus a central descending lightning stroke.
            float mainX = 0.50 + fracture(uv.y, 91.0) * 0.045;
            float mainBolt = abs(uv.x - mainX) + max(0.0, 0.20 - uv.y);
            float forkX = 0.43 - uv.y * 0.65 + fracture(uv.y, 32.0) * 0.025;
            float fork = abs(abs(uv.x - 0.5) - forkX) + max(0.0, uv.y - 0.64);
            float lowerFork = abs(abs(uv.x - 0.5) - (uv.y - 0.52) * 0.50)
                    + max(0.0, 0.52 - uv.y) + max(0.0, uv.y - 0.90);
            dist = min(mainBolt, min(fork, lowerFork));
            // Small diamond discharges beside the primary weave.
            float spark = abs(abs(abs(uv.x - 0.5) - 0.29) + abs(uv.y - 0.64) - 0.065);
            dist = min(dist, spark + max(0.0, abs(uv.y - 0.64) - 0.10));
        } else if (family < 2.5) {
            // The crown is an open lightning lattice, visible from above.
            float spine = abs(uv.x - 0.5 - fracture(uv.y, 53.0) * 0.055);
            float cross = abs(uv.y - 0.5 - fracture(uv.x, 62.0) * 0.045);
            float border = min(abs(uv.x - 0.10), abs(uv.x - 0.90));
            dist = min(spine, min(cross, border));
        } else {
            // A strong, pixel-stepped lightning brow reads even from a straight front view.
            float browY = 0.48 + fracture(uv.x, 81.0) * 0.16;
            float brow = abs(uv.y - browY) * 0.22;
            float crest = abs(abs(uv.x - 0.5) * 1.8 + abs(uv.y - 0.45) * 0.30 - 0.14);
            dist = min(brow, crest);
        }
        float stroke = 1.0 - smoothstep(0.014, 0.035 + aa * 0.4, dist);
        float glow = exp(-dist * 38.0);
        float pulse = 0.84 + 0.16 * sin(time * 0.09 - uv.y * 11.0);
        bolt = stroke * pulse;
        halo = glow;
        embroidery = 1.0;
        edge = 0.0;
        rim = 0.0;
    }
    bolt = min(bolt, 1.0) * (1.0 - outer);
    halo = min(halo, 1.0) * (1.0 - outer);
    float flow = 0.5 + 0.5 * sin(uv.y * 9.0 - time * 0.020 + cos(uv.x * 5.0));
    float fill = (0.10 + rim * 0.12 + flow * 0.05) * (1.0 - embroidery);
    float alpha = shape * ends * (fill + edge * 0.34 + bolt * 0.78 + halo * 0.23);
    alpha *= mix(1.0, 0.25, outer) * vertexColor.a * ColorModulator.a;
    if (alpha < 0.003) discard;
    vec3 base = vertexColor.rgb;
    vec3 body = mix(base * 0.62, base, flow);
    vec3 highlight = mix(base, vec3(1.0), 0.83);
    vec3 energy = mix(body, highlight, clamp(bolt * 0.90 + edge * 0.45 + halo * 0.12, 0.0, 1.0));
    fragColor = vec4(energy * ColorModulator.rgb, min(alpha, 0.94));
}
