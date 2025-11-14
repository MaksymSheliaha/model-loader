package com.example.graphics;

import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Loads a cubemap texture from 6 image files inside a folder.
 * Expected file names: posx.jpg, negx.jpg, posy.jpg, negy.jpg, posz.jpg, negz.jpg (or .png)
 */
public class CubeMapTexture {
    private final int id;

    public CubeMapTexture(String folderPath) {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, id);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        String[] faces = {"px", "nx", "py", "ny", "pz", "nz"};
        for (int i = 0; i < faces.length; i++) {
            String base = folderPath + "/" + faces[i];
            String tried = tryLoadImage(base + ".png", GL_TEXTURE_CUBE_MAP_POSITIVE_X + i);
            if (tried == null) {
                tried = tryLoadImage(base + ".jpg", GL_TEXTURE_CUBE_MAP_POSITIVE_X + i);
            }
            if (tried == null) {
                throw new RuntimeException("Failed to load cubemap face: " + base + "(.jpg/.png)");
            }
        }
        // Optional mipmaps (not required if using only background sampling)
        glGenerateMipmap(GL_TEXTURE_CUBE_MAP);
    }

    private String tryLoadImage(String resourcePath, int target) {
        try {
            ByteBuffer imageBuffer = ioResourceToByteBuffer(resourcePath, 8 * 1024);
            try (var stack = stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);
                STBImage.stbi_set_flip_vertically_on_load(false); // no flip for cubemap
                ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, w, h, comp, 0);
                if (image == null) return null;
                int channels = comp.get(0);
                int format = channels == 4 ? GL_RGBA : GL_RGB;
                int internal = channels == 4 ? GL_RGBA8 : GL_RGB8;
                glTexImage2D(target, 0, internal, w.get(0), h.get(0), 0, format, GL_UNSIGNED_BYTE, image);
                STBImage.stbi_image_free(image);
                return resourcePath;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        try (InputStream source = CubeMapTexture.class.getClassLoader().getResourceAsStream(resource)) {
            if (source == null) throw new IOException("Resource not found: " + resource);
            byte[] data = source.readAllBytes();
            ByteBuffer buffer = memAlloc(data.length);
            buffer.put(data).flip();
            return buffer;
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_CUBE_MAP, id);
    }

    public void delete() { glDeleteTextures(id); }
}

