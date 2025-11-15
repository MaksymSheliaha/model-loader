#version 330 core
out vec4 FragColor;

in vec3 vNormal;
in vec3 vFragPos;
in vec2 vTex;

uniform sampler2D uTexture;
uniform samplerCube uEnvMap;
uniform bool uReflect;
uniform float uReflectStrength;
uniform float uGlow;

const int MAX_LIGHTS = 16;
uniform int uLightCount;
uniform vec3 uLightPos[MAX_LIGHTS];
uniform vec3 uLightColor[MAX_LIGHTS];

uniform vec3 uViewPos;
uniform float uAmbient;
uniform float uSpecularStrength;
uniform float uShininess;
uniform bool uEmissive;
uniform bool uUnlit;

uniform int uShadowEnabled;
uniform float uShadowFarPlane;
// Individual shadow map samplers (support up to 12 bottles)
uniform samplerCube uShadowMap0;
uniform samplerCube uShadowMap1;
uniform samplerCube uShadowMap2;
uniform samplerCube uShadowMap3;
uniform samplerCube uShadowMap4;
uniform samplerCube uShadowMap5;
uniform samplerCube uShadowMap6;
uniform samplerCube uShadowMap7;
uniform samplerCube uShadowMap8;
uniform samplerCube uShadowMap9;
uniform samplerCube uShadowMap10;
uniform samplerCube uShadowMap11;

float sampleShadow(int idx, vec3 dir) {
    if (idx == 0) return texture(uShadowMap0, dir).r;
    else if (idx == 1) return texture(uShadowMap1, dir).r;
    else if (idx == 2) return texture(uShadowMap2, dir).r;
    else if (idx == 3) return texture(uShadowMap3, dir).r;
    else if (idx == 4) return texture(uShadowMap4, dir).r;
    else if (idx == 5) return texture(uShadowMap5, dir).r;
    else if (idx == 6) return texture(uShadowMap6, dir).r;
    else if (idx == 7) return texture(uShadowMap7, dir).r;
    else if (idx == 8) return texture(uShadowMap8, dir).r;
    else if (idx == 9) return texture(uShadowMap9, dir).r;
    else if (idx == 10) return texture(uShadowMap10, dir).r;
    else if (idx == 11) return texture(uShadowMap11, dir).r;
    return 1.0; // default (no shadow info)
}

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
        float shadowFactor = 1.0;
        if (uShadowEnabled == 1 && !uUnlit && i < 12) {
            vec3 sampleDir = normalize(vFragPos - uLightPos[i]);
            float closestDepth = sampleShadow(i, sampleDir) * uShadowFarPlane;
            float currentDepth = dist;
            float bias = 0.01;
            float shadow = currentDepth - bias > closestDepth ? 1.0 : 0.0;
            shadowFactor = 1.0 - shadow;
        }
        lighting += shadowFactor * attenuation * (diff * baseColor + spec * uLightColor[i]);
    }

    if (uEmissive) {
        lighting += 0.35 * vec3(1.0);
    }

    if (uReflect) {
        vec3 I = normalize(vFragPos - uViewPos);
        vec3 R = reflect(I, N);
        vec3 envCol = texture(uEnvMap, R).rgb;
        lighting = mix(lighting, envCol, clamp(uReflectStrength, 0.0, 1.0));
    }

    lighting += uGlow * vec3(1.0);

    FragColor = vec4(lighting, 1.0);
}
