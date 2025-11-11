package com.example.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private final Vector3f position = new Vector3f(0, 0, 3);
    private float yaw = -90f; // facing -Z
    private float pitch = 0f;

    public Matrix4f getViewMatrix() {
        Vector3f front = new Vector3f(
                (float)Math.cos(Math.toRadians(yaw)) * (float)Math.cos(Math.toRadians(pitch)),
                (float)Math.sin(Math.toRadians(pitch)),
                (float)Math.sin(Math.toRadians(yaw)) * (float)Math.cos(Math.toRadians(pitch))
        ).normalize();
        Vector3f up = new Vector3f(0,1,0);
        Vector3f center = new Vector3f(position).add(front);
        return new Matrix4f().lookAt(position, center, up);
    }

    public void move(Vector3f delta) { position.add(delta); }

    public void processKeyboard(boolean forward, boolean back, boolean left, boolean right, float deltaTime) {
        float speed = 2.5f * deltaTime;
        Vector3f front = new Vector3f(
                (float)Math.cos(Math.toRadians(yaw)) * (float)Math.cos(Math.toRadians(pitch)),
                (float)Math.sin(Math.toRadians(pitch)),
                (float)Math.sin(Math.toRadians(yaw)) * (float)Math.cos(Math.toRadians(pitch))
        ).normalize();
        Vector3f rightVec = new Vector3f(front).cross(new Vector3f(0,1,0)).normalize();
        if (forward) position.add(new Vector3f(front).mul(speed));
        if (back) position.sub(new Vector3f(front).mul(speed));
        if (left) position.sub(new Vector3f(rightVec).mul(speed));
        if (right) position.add(new Vector3f(rightVec).mul(speed));
    }

    public void processMouse(float xoffset, float yoffset) {
        float sensitivity = 0.1f;
        yaw += xoffset * sensitivity;
        pitch -= yoffset * sensitivity;
        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;
    }

    public Vector3f getPosition() { return position; }
}

