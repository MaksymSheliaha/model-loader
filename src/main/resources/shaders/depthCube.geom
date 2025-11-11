#version 330 core
layout (triangles) in;
layout (triangle_strip, max_vertices = 18) out;

uniform mat4 uShadowMatrices[6];

in vec3 vWorldPos[];

out vec3 gWorldPos;

void main() {
    for (int face = 0; face < 6; ++face) {
        for (int i = 0; i < 3; ++i) {
            gl_Position = uShadowMatrices[face] * vec4(vWorldPos[i], 1.0);
            gWorldPos = vWorldPos[i];
            EmitVertex();
        }
        EndPrimitive();
    }
}

