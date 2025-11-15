package com.example.graphics;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Simple skybox renderer: creates a cube VAO/VBO and renders with a cubemap texture.
 */
public class Skybox {
    private final int vao;
    private final int vbo;
    private final CubeMapTexture texture;

    private static final float[] CUBE_VERTS = {
            -1f,  1f, -1f,
            -1f, -1f, -1f,
             1f, -1f, -1f,
             1f, -1f, -1f,
             1f,  1f, -1f,
            -1f,  1f, -1f,

            -1f, -1f,  1f,
            -1f, -1f, -1f,
            -1f,  1f, -1f,
            -1f,  1f, -1f,
            -1f,  1f,  1f,
            -1f, -1f,  1f,

             1f, -1f, -1f,
             1f, -1f,  1f,
             1f,  1f,  1f,
             1f,  1f,  1f,
             1f,  1f, -1f,
             1f, -1f, -1f,

            -1f, -1f,  1f,
            -1f,  1f,  1f,
             1f,  1f,  1f,
             1f,  1f,  1f,
             1f, -1f,  1f,
            -1f, -1f,  1f,

            -1f,  1f, -1f,
             1f,  1f, -1f,
             1f,  1f,  1f,
             1f,  1f,  1f,
            -1f,  1f,  1f,
            -1f,  1f, -1f,

            -1f, -1f, -1f,
            -1f, -1f,  1f,
             1f, -1f, -1f,
             1f, -1f, -1f,
            -1f, -1f,  1f,
             1f, -1f,  1f
    };

    public Skybox(CubeMapTexture texture) {
        this.texture = texture;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, CUBE_VERTS, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glBindVertexArray(0);
    }

    public void render() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);
    }

    public void bindTexture(int unit) {
        texture.bind(unit);
    }

    public void delete() {
        texture.delete();
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}

