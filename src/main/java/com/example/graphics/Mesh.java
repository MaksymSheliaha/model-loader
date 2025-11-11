package com.example.graphics;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Mesh {
    private final int vao;
    private final int vbo;
    private final int ebo;
    private final int vertexCount;

    public Mesh(float[] vertices, int[] indices) {
        this.vertexCount = indices.length;
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer vb = memAllocFloat(vertices.length);
        vb.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        memFree(vb);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = memAllocInt(indices.length);
        ib.put(indices).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        memFree(ib);

        int stride = 8 * Float.BYTES; // pos(3)+normal(3)+tex(2)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);

        glBindVertexArray(0);
    }

    public void render() {
        glBindVertexArray(vao);
        glDrawElements(GL11.GL_TRIANGLES, vertexCount, GL11.GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void delete() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
    }
}

