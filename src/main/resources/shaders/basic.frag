#version 330 core
out vec4 FragColor;

in vec3 vNormal;
in vec3 vFragPos;
in vec2 vTex;

uniform sampler2D uTexture;
uniform samplerCube uEnvMap; // Environment map for reflections
uniform bool uReflect; // Enable or disable reflections
uniform float uReflectStrength; // Strength of the reflection
uniform float uGlow; // Glow intensity

const int MAX_LIGHTS = 16;
uniform int uLightCount;
uniform vec3 uLightPos[MAX_LIGHTS];
uniform vec3 uLightColor[MAX_LIGHTS];

uniform vec3 uViewPos;
uniform float uAmbient;
uniform float uSpecularStrength;
uniform float uShininess;
uniform bool uEmissive;
uniform bool uUnlit; // if true, render texture without lighting

void main() {
    vec3 baseColor = texture(uTexture, vTex).rgb;

    if (uUnlit) {
        FragColor = vec4(baseColor, 1.0);
        return;
    }

    vec3 N = normalize(vNormal);
    vec3 V = normalize(uViewPos - vFragPos);

    vec3 lighting = uAmbient * baseColor;

    for (int i = 0; i < uLightCount; ++i) {
        vec3 Lvec = uLightPos[i] - vFragPos;
        float dist = length(Lvec);
        vec3 L = Lvec / dist;
        float diff = max(dot(N, L), 0.0);
        vec3 H = normalize(L + V);
        float spec = pow(max(dot(N, H), 0.0), uShininess) * uSpecularStrength;
        float attenuation = 1.0 / (1.0 + 0.09 * dist + 0.032 * dist * dist);
        lighting += attenuation * (diff * baseColor + spec * uLightColor[i]);
    }

    if (uEmissive) {
        lighting += 0.35 * vec3(1.0);
    }

    // Environment reflection
    if (uReflect) {
        vec3 I = normalize(vFragPos - uViewPos); // Incident vector
        vec3 R = reflect(I, normalize(vNormal)); // Reflection vector
        vec3 envCol = texture(uEnvMap, R).rgb; // Sample environment map
        lighting = mix(lighting, envCol, clamp(uReflectStrength, 0.0, 1.0)); // Mix with existing lighting
    }

    // Glow effect
    lighting += uGlow * vec3(1.0); // Additive glow

    FragColor = vec4(lighting, 1.0);
}
