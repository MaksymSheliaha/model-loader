package com.example.graphics;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

public class Texture {
    private final int id;

    public Texture(String resourcePath) {
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        try {
            ByteBuffer imageBuffer = ioResourceToByteBuffer(resourcePath, 8 * 1024);
            try (var stack = stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);
                STBImage.stbi_set_flip_vertically_on_load(true);
                ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, w, h, comp, 4);
                if (image == null) throw new RuntimeException("Failed to load texture: " + resourcePath + " - " + STBImage.stbi_failure_reason());
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
                glGenerateMipmap(GL_TEXTURE_2D);
                STBImage.stbi_image_free(image);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
        try (InputStream source = Texture.class.getClassLoader().getResourceAsStream(resource)) {
            if (source == null) throw new IOException("Resource not found: " + resource);
            byte[] data = source.readAllBytes();
            ByteBuffer buffer = memAlloc(data.length);
            buffer.put(data).flip();
            return buffer;
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void delete() { glDeleteTextures(id); }
}

