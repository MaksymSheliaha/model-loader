#version 330 core
out vec4 FragColor;

in vec3 vNormal;
in vec3 vFragPos;
in vec2 vTex;

uniform sampler2D uTexture;
uniform samplerCube uDepthCube;

uniform vec3 uLightPos;
uniform vec3 uLightColor;
uniform vec3 uViewPos;
uniform float uFar;
uniform float uAmbient;
uniform float uSpecularStrength;
uniform float uShininess;
uniform bool uEmissive;

float sampleShadow(vec3 fragPos) {
    vec3 L = fragPos - uLightPos;
    float currentDist = length(L);
    float visibility = 0.0;
    int samples = 20;
    float diskRadius = 0.15 * currentDist / uFar; // distance-based disk radius
    for (int i = 0; i < samples; ++i) {
        // Poisson-like sample offsets
        vec3 offset = normalize(vec3(
            fract(sin(float(i) * 12.9898) * 43758.5453),
            fract(sin(float(i) * 78.233) * 9623.5453),
            fract(sin(float(i) * 4.1254) * 1234.1234)) * 2.0 - 1.0);
        float closest = texture(uDepthCube, L + offset * diskRadius).r * uFar; // stored normalized
        visibility += currentDist - 0.005 <= closest ? 1.0 : 0.0;
    }
    return visibility / samples;
}

void main() {
    vec3 baseColor = texture(uTexture, vTex).rgb;
    vec3 N = normalize(vNormal);
    vec3 L = normalize(uLightPos - vFragPos);
    vec3 V = normalize(uViewPos - vFragPos);
    vec3 H = normalize(L + V);

    float diff = max(dot(N, L), 0.0);
    float spec = pow(max(dot(N, H), 0.0), uShininess) * uSpecularStrength;

    float dist = length(uLightPos - vFragPos);
    float attenuation = 1.0 / (1.0 + 0.09 * dist + 0.032 * dist * dist);

    float shadow = sampleShadow(vFragPos);

    vec3 ambient = uAmbient * baseColor;
    vec3 lighting = ambient + shadow * attenuation * (diff * baseColor + spec * uLightColor);

    if (uEmissive) lighting += 0.6 * uLightColor;

    FragColor = vec4(lighting, 1.0);
}
