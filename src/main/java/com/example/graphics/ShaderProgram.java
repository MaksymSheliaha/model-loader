package com.example.graphics;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    private final int programId;

    public ShaderProgram(String vertexResource, String fragmentResource) {
        int vertexId = createShaderFromResource(vertexResource, GL20.GL_VERTEX_SHADER);
        int fragmentId = createShaderFromResource(fragmentResource, GL20.GL_FRAGMENT_SHADER);
        programId = glCreateProgram();
        glAttachShader(programId, vertexId);
        glAttachShader(programId, fragmentId);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Shader link error: " + glGetProgramInfoLog(programId));
        }
        glDetachShader(programId, vertexId);
        glDetachShader(programId, fragmentId);
        glDeleteShader(vertexId);
        glDeleteShader(fragmentId);
    }

    private int createShaderFromResource(String path, int type) {
        String source = readResource(path);
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Shader compile error (" + path + "): " + glGetShaderInfoLog(id));
        }
        return id;
    }

    private String readResource(String path) {
        try (InputStream is = ShaderProgram.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Resource not found: " + path);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void use() {
        glUseProgram(programId);
    }

    public void delete() {
        glDeleteProgram(programId);
    }

    public int getUniformLocation(String name) {
        return glGetUniformLocation(programId, name);
    }

    public int id() { return programId; }
}

