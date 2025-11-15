package com.example.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.glFramebufferTexture;

/**
 * Manages a depth cubemap for a single point light.
 */
public class PointLightShadowMap {
    public final int fbo;
    public final int depthCubeTex;
    public final int size;
    public final float farPlane;

    public PointLightShadowMap(int size, float farPlane) {
        this.size = size;
        this.farPlane = farPlane;
        fbo = glGenFramebuffers();
        depthCubeTex = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubeTex);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_DEPTH_COMPONENT, size, size, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthCubeTex, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Shadow framebuffer incomplete: status=" + status);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindForWrite() {
        glViewport(0, 0, size, size);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void delete() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthCubeTex);
    }

    public static Matrix4f[] buildLightSpaceMatrices(Vector3f lightPos, float nearPlane, float farPlane) {
        Matrix4f proj = new Matrix4f().perspective((float)Math.toRadians(90.0), 1.0f, nearPlane, farPlane);
        Matrix4f[] mats = new Matrix4f[6];
        mats[0] = new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(1,0,0), new Vector3f(0,-1,0))); // +X
        mats[1] = new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(-1,0,0), new Vector3f(0,-1,0))); // -X
        mats[2] = new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0,1,0), new Vector3f(0,0,1))); // +Y
        mats[3] = new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0,-1,0), new Vector3f(0,0,-1))); // -Y
        mats[4] = new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0,0,1), new Vector3f(0,-1,0))); // +Z
        mats[5] = new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(0,0,-1), new Vector3f(0,-1,0))); // -Z
        return mats;
    }
}

