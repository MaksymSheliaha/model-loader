#version 330 core

in vec3 vWorldPos;

uniform vec3 uLightPos;
uniform float uFarPlane;

void main() {
    float dist = length(vWorldPos - uLightPos);
    // Store linear depth scaled to [0,1]
    gl_FragDepth = dist / uFarPlane;
}

