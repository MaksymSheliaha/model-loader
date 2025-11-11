package com.example.graphics;

import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.*;

public class ModelLoader {
    public static Model loadObjWithTexture(String objResourcePath, String textureResourcePath) {
        AIScene scene = aiImportFileFromMemory(Utils.ioResourceToByteBuffer(objResourcePath),
                aiProcess_Triangulate | aiProcess_GenNormals | aiProcess_JoinIdenticalVertices | aiProcess_ImproveCacheLocality,
                (String) null);
        if (scene == null) {
            throw new RuntimeException("Assimp load failed: " + aiGetErrorString());
        }
        Texture texture = new Texture(textureResourcePath);
        Model model = new Model(texture);

        int meshCount = scene.mNumMeshes();
        PointerBuffer meshes = scene.mMeshes();
        for (int i = 0; i < meshCount; i++) {
            AIMesh mesh = AIMesh.create(meshes.get(i));
            float[] vertices = extractVertices(mesh);
            int[] indices = extractIndices(mesh);
            model.addMesh(new Mesh(vertices, indices));
        }
        aiReleaseImport(scene);
        return model;
    }

    private static float[] extractVertices(AIMesh mesh) {
        AIVector3D.Buffer positions = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        AIVector3D.Buffer texCoords = mesh.mTextureCoords(0);
        int vertexCount = mesh.mNumVertices();
        float[] verts = new float[vertexCount * 8];
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D p = positions.get(i);
            AIVector3D n = normals != null ? normals.get(i) : AIVector3D.create().set(0,0,1);
            AIVector3D t = texCoords != null ? texCoords.get(i) : AIVector3D.create().set(0,0,0);
            int base = i * 8;
            verts[base] = p.x();
            verts[base+1] = p.y();
            verts[base+2] = p.z();
            verts[base+3] = n.x();
            verts[base+4] = n.y();
            verts[base+5] = n.z();
            verts[base+6] = t.x();
            verts[base+7] = t.y();
        }
        return verts;
    }

    private static int[] extractIndices(AIMesh mesh) {
        int faceCount = mesh.mNumFaces();
        AIFace.Buffer faces = mesh.mFaces();
        List<Integer> indexList = new ArrayList<>(faceCount * 3);
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faces.get(i);
            IntBuffer indices = face.mIndices();
            for (int j = 0; j < indices.remaining(); j++) {
                indexList.add(indices.get(j));
            }
        }
        int[] arr = new int[indexList.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = indexList.get(i);
        return arr;
    }

    // Tiny utility to read resources as ByteBuffer for Assimp
    private static class Utils {
        static java.nio.ByteBuffer ioResourceToByteBuffer(String resource) {
            try (java.io.InputStream is = ModelLoader.class.getClassLoader().getResourceAsStream(resource)) {
                if (is == null) throw new RuntimeException("Resource not found: " + resource);
                byte[] bytes = is.readAllBytes();
                java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
                buffer.put(bytes).flip();
                return buffer;
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

