#version 330 core
out vec4 FragColor;

in vec3 vTexDir;

uniform samplerCube uSkybox;

void main() {
    vec3 dir = normalize(vTexDir);
    vec3 color = texture(uSkybox, dir).rgb;
    FragColor = vec4(color, 1.0);
}

