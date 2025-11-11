package com.example.graphics;

import org.joml.Vector3f;

import java.util.List;
import java.util.ArrayList;

public class Model {
    private final List<Mesh> meshes = new ArrayList<>();
    private final Texture texture; // Single texture for simplicity

    // Bounding box in model space
    private Vector3f boundsMin = new Vector3f(0,0,0);
    private Vector3f boundsMax = new Vector3f(0,0,0);

    public Model(Texture texture) { this.texture = texture; }

    public void addMesh(Mesh mesh) { meshes.add(mesh); }

    public void render() {
        texture.bind(0);
        for (Mesh mesh : meshes) {
            mesh.render();
        }
    }

    public void delete() {
        for (Mesh m : meshes) m.delete();
        texture.delete();
    }

    public void setBounds(Vector3f min, Vector3f max) {
        this.boundsMin.set(min);
        this.boundsMax.set(max);
    }

    public Vector3f getBoundsMin() { return new Vector3f(boundsMin); }
    public Vector3f getBoundsMax() { return new Vector3f(boundsMax); }

    /**
     * Returns the largest side length of the bounding box (max of width/height/depth).
     */
    public float getMaxExtent() {
        float sx = Math.abs(boundsMax.x - boundsMin.x);
        float sy = Math.abs(boundsMax.y - boundsMin.y);
        float sz = Math.abs(boundsMax.z - boundsMin.z);
        return Math.max(sx, Math.max(sy, sz));
    }
}
