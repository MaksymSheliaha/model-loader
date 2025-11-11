package com.example.graphics;

import java.util.List;
import java.util.ArrayList;

public class Model {
    private final List<Mesh> meshes = new ArrayList<>();
    private final Texture texture; // Single texture for simplicity

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
}

