#version 330 core
out vec4 FragColor;

in vec3 vNormal;
in vec3 vFragPos;
in vec2 vTex;

uniform sampler2D uTexture;
uniform vec3 uLightDir;

void main() {
    vec3 norm = normalize(vNormal);
    float diff = max(dot(norm, -uLightDir), 0.1);
    vec4 texColor = texture(uTexture, vTex);
    FragColor = vec4(texColor.rgb * diff, texColor.a);
}

