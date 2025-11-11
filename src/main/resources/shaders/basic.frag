#version 330 core
out vec4 FragColor;

in vec3 vNormal;
in vec3 vFragPos;
in vec2 vTex;

uniform sampler2D uTexture;

uniform vec3 uLightPos;     // world-space position of the light (cyborg)
uniform vec3 uLightColor;   // light color/intensity
uniform vec3 uViewPos;      // camera position
uniform float uAmbient;     // ambient intensity (set near 0 to make backside dark)
uniform float uSpecularStrength; // intensity of specular term
uniform float uShininess;        // shininess exponent
uniform bool uEmissive;          // if true, emit light-like color

void main() {
    vec3 color = texture(uTexture, vTex).rgb;
    vec3 norm = normalize(vNormal);
    vec3 lightDir = normalize(uLightPos - vFragPos);
    vec3 viewDir = normalize(uViewPos - vFragPos);

    // Diffuse (Lambert); clamped to [0,1]
    float diff = max(dot(norm, lightDir), 0.0);

    // Specular (Blinn-Phong)
    vec3 halfwayDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(norm, halfwayDir), 0.0), uShininess) * uSpecularStrength;

    vec3 ambient = uAmbient * color;
    vec3 lighting = ambient + (diff * color + spec * uLightColor);

    if (uEmissive) {
        lighting += 0.6 * uLightColor; // glow for the light source object
    }

    FragColor = vec4(lighting, 1.0);
}
