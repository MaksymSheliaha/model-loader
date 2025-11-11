#version 330 core
out vec4 FragColor;

in vec3 vNormal;
in vec3 vFragPos;
in vec2 vTex;

uniform sampler2D uTexture;

const int MAX_LIGHTS = 16;
uniform int uLightCount;
uniform vec3 uLightPos[MAX_LIGHTS];
uniform vec3 uLightColor[MAX_LIGHTS];

uniform vec3 uViewPos;
uniform float uAmbient;
uniform float uSpecularStrength;
uniform float uShininess;
uniform bool uEmissive;

void main() {
    vec3 baseColor = texture(uTexture, vTex).rgb;
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
        // Give a glow to emissive objects (bottles)
        lighting += 0.35 * vec3(1.0);
    }

    FragColor = vec4(lighting, 1.0);
}
