package com.example;

import com.example.graphics.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class ModelViewer {
    private long window;
    private int width = 1280;
    private int height = 720;

    private ShaderProgram shader;
    private Camera camera = new Camera();

    private Model coronaModel;
    private Model cyborgModel;

    private float lastX = width / 2f;
    private float lastY = height / 2f;
    private boolean firstMouse = true;

    private float deltaTime;
    private float lastFrame;

    public static void main(String[] args) { new ModelViewer().run(); }

    public void run() {
        initWindow();
        initGL();
        initScene();
        loop();
        cleanup();
    }

    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(width, height, "Model Viewer", 0, 0);
        if (window == 0) throw new RuntimeException("Failed to create window");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (firstMouse) { lastX = (float)xpos; lastY = (float)ypos; firstMouse = false; }
            float xoff = (float)xpos - lastX;
            float yoff = (float)ypos - lastY;
            lastX = (float)xpos; lastY = (float)ypos;
            camera.processMouse(xoff, yoff);
        });
    }

    private void initGL() {
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
    }

    private void initScene() {
        shader = new ShaderProgram("shaders/basic.vert", "shaders/basic.frag");
        // Bind sampler to texture unit 0 once
        int texLoc = shader.getUniformLocation("uTexture");
        glUseProgram(shader.id());
        glUniform1i(texLoc, 0);

        // Load models using Assimp + STB
        coronaModel = ModelLoader.loadObjWithTexture("model/corona/Corona.obj", "model/corona/BotellaText.jpg");
        cyborgModel = ModelLoader.loadObjWithTexture("model/cyborg/cyborg.obj", "model/cyborg/cyborg_diffuse.png");
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            float current = (float)glfwGetTime();
            deltaTime = current - lastFrame; lastFrame = current;
            processInput();

            glViewport(0, 0, width, height);
            glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shader.use();
            int projLoc = shader.getUniformLocation("uProjection");
            int viewLoc = shader.getUniformLocation("uView");
            int modelLoc = shader.getUniformLocation("uModel");
            int lightLoc = shader.getUniformLocation("uLightDir");
            glUniform3f(lightLoc, -0.2f, -1.0f, -0.3f);

            try (var stack = stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(60), (float)width/height, 0.1f, 100f);
                glUniformMatrix4fv(projLoc, false, projection.get(fb));
                Matrix4f view = camera.getViewMatrix();
                glUniformMatrix4fv(viewLoc, false, view.get(fb));

                // Draw cyborg in the center
                Matrix4f cybM = new Matrix4f()
                        .translate(0.0f, 0.0f, 0.0f)
                        .scale(0.01f);
                glUniformMatrix4fv(modelLoc, false, cybM.get(fb));
                cyborgModel.render();

                // Draw eight corona models around it in XZ plane with varying distances
                int count = 8;
                float baseRadius = 3.5f;
                float var = 1.2f;
                for (int i = 0; i < count; i++) {
                    float angle = (float)(2.0 * Math.PI * i / count);
                    float radius = baseRadius + ((i % 3) * var); // vary distance
                    float x = (float)Math.cos(angle) * radius;
                    float z = (float)Math.sin(angle) * radius;
                    Matrix4f coronaM = new Matrix4f()
                            .translate(x, 0.0f, z)
                            .rotateY(-angle) // face roughly toward center
                            .scale(0.02f);
                    glUniformMatrix4fv(modelLoc, false, coronaM.get(fb));
                    coronaModel.render();
                }
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void processInput() {
        boolean forward = glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS;
        boolean back = glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS;
        boolean left = glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS;
        boolean right = glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS;
        camera.processKeyboard(forward, back, left, right, deltaTime);

        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) glfwSetWindowShouldClose(window, true);
    }

    private void cleanup() {
        if (coronaModel != null) coronaModel.delete();
        if (cyborgModel != null) cyborgModel.delete();
        shader.delete();
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
