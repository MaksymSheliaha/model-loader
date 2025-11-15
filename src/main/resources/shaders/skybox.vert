#version 330 core
layout (location = 0) in vec3 aPos;

out vec3 vTexDir;

uniform mat4 uProjection;
uniform mat4 uViewRot; // view matrix without translation

void main() {
    vTexDir = aPos;
    vec4 pos = uProjection * uViewRot * vec4(aPos, 1.0);
    gl_Position = pos.xyww; // force depth to 1.0 (far plane)
}

