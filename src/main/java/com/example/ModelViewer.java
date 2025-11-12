package com.example;

import com.example.graphics.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class ModelViewer {
    private long window;
    private int width = 1280;
    private int height = 720;

    private ShaderProgram shader;
    private Camera camera = new Camera();

    // Bottles collection (6 distinct models)
    private Model[] bottles;
    private float[] bottleScale;
    private float[] bottleOrientX;
    private float[] bottleOrientZ;
    private int[] bottleSpinAxis; // 0=Y, 1=X, 2=Z

    // Center model
    private Model cyborgModel;
    private float cyborgScale = 1.0f;

    private float lastX = width / 2f;
    private float lastY = height / 2f;
    private boolean firstMouse = true;

    private float deltaTime;
    private float lastFrame;

    private float orbitSpeedScale = 1.0f;

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
        int texLoc = shader.getUniformLocation("uTexture");
        glUseProgram(shader.id());
        glUniform1i(texLoc, 0);

        // Center model
        cyborgModel = ModelLoader.loadObjWithTexture("model/cyborg/cyborg.obj", "model/cyborg/cyborg_diffuse.png");
        float targetSize = 2.0f;
        cyborgScale = targetSize / Math.max(1e-6f, cyborgModel.getMaxExtent());
        cyborgScale *= 1.3f; // трохи більший кіборг

        // Six distinct bottle models/textures from resources/model
        String[][] bottleRes = new String[][]{
                {"model/beer-v1/beer.obj", "model/beer-v1/14043_16_oz._Beer_Bottle_diff.jpg"},
                {"model/beer-v2/beer.obj", "model/beer-v2/14043_16_oz._Beer_Bottle_diff_final.jpg"},
                {"model/bud/bud.obj", "model/bud/BUD2.jpeg"},
                {"model/corona/Corona.obj", "model/corona/BotellaText.jpg"},
                {"model/heineken/heineken.obj", "model/heineken/material_baseColor.png"},
                {"model/stella/stella-artois.obj", "model/stella/STELLAARTOIS2.png"}
        };
        bottles = new Model[bottleRes.length];
        bottleScale = new float[bottleRes.length];
        bottleOrientX = new float[bottleRes.length];
        bottleOrientZ = new float[bottleRes.length];
        bottleSpinAxis = new int[bottleRes.length];

        for (int i = 0; i < bottleRes.length; i++) {
            bottles[i] = ModelLoader.loadObjWithTexture(bottleRes[i][0], bottleRes[i][1]);
            float extent = Math.max(1e-6f, bottles[i].getMaxExtent());
            bottleScale[i] = targetSize / extent;

            // Визначення найдовшої осі для орієнтації вертикально
            Vector3f min = bottles[i].getBoundsMin();
            Vector3f max = bottles[i].getBoundsMax();
            float dx = Math.abs(max.x - min.x);
            float dy = Math.abs(max.y - min.y);
            float dz = Math.abs(max.z - min.z);
            boolean xLongest = dx >= dy && dx >= dz;
            boolean zLongest = dz >= dx && dz >= dy;

            String modelPath = bottleRes[i][0];
            boolean flip = modelPath.contains("bud") || modelPath.contains("stella") || modelPath.contains("heineken");

            if (xLongest) {
                bottleOrientZ[i] = (float)Math.toRadians(flip ? -90.0 : 90.0); // X -> Y
                bottleOrientX[i] = 0.0f;
                bottleSpinAxis[i] = 1; // обертання навколо (початкової) X -> стане Y
            } else if (zLongest) {
                bottleOrientX[i] = (float)Math.toRadians(flip ? 90.0 : -90.0); // Z -> Y
                bottleOrientZ[i] = 0.0f;
                bottleSpinAxis[i] = 2; // обертання навколо (початкової) Z -> стане Y
            } else {
                bottleOrientX[i] = 0.0f;
                bottleOrientZ[i] = 0.0f;
                bottleSpinAxis[i] = 0; // вже по Y
            }
        }
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            float current = (float)glfwGetTime();
            deltaTime = current - lastFrame; lastFrame = current;
            processInput();

            glViewport(0, 0, width, height);
            glClearColor(0.02f, 0.02f, 0.03f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shader.use();
            int projLoc = shader.getUniformLocation("uProjection");
            int viewLoc = shader.getUniformLocation("uView");
            int modelLoc = shader.getUniformLocation("uModel");
            int lightCountLoc = shader.getUniformLocation("uLightCount");
            int viewPosLoc = shader.getUniformLocation("uViewPos");
            int ambientLoc = shader.getUniformLocation("uAmbient");
            int specLoc = shader.getUniformLocation("uSpecularStrength");
            int shinLoc = shader.getUniformLocation("uShininess");
            int emissiveLoc = shader.getUniformLocation("uEmissive");
            int unlitLoc = shader.getUniformLocation("uUnlit");

            try (var stack = stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(60), (float)width/height, 0.1f, 100f);
                glUniformMatrix4fv(projLoc, false, projection.get(fb));
                Matrix4f view = camera.getViewMatrix();
                glUniformMatrix4fv(viewLoc, false, view.get(fb));

                glUniform3f(viewPosLoc, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
                glUniform1f(ambientLoc, 0.03f);
                glUniform1f(specLoc, 0.7f);
                glUniform1f(shinLoc, 48.0f);

                int count = bottles.length; // 6 lights from bottles
                float baseRadius = 4.5f;
                glUniform1i(lightCountLoc, count);
                for (int i = 0; i < count; i++) {
                    float baseAngle = (float)(2.0 * Math.PI * i / count);
                    float orbitFreq = 0.3f + 0.15f * (i % 7);
                    float angleTotal = baseAngle + current * orbitFreq * orbitSpeedScale;
                    float x = (float)Math.cos(angleTotal) * baseRadius;
                    float z = (float)Math.sin(angleTotal) * baseRadius;
                    int uPosLoc = glGetUniformLocation(shader.id(), "uLightPos[" + i + "]");
                    int uColLoc = glGetUniformLocation(shader.id(), "uLightColor[" + i + "]");
                    glUniform3f(uPosLoc, x, 0.0f, z);
                    glUniform3f(uColLoc, 1.0f, 0.95f, 0.85f);
                }

                // Cyborg (lit)
                glUniform1i(unlitLoc, 0);
                glUniform1i(emissiveLoc, 0);
                Matrix4f cybM = new Matrix4f().scale(cyborgScale);
                glUniformMatrix4fv(modelLoc, false, cybM.get(fb));
                cyborgModel.render();

                // Bottles (unlit + emissive)
                for (int i = 0; i < bottles.length; i++) {
                    float baseAngle = (float)(2.0 * Math.PI * i / bottles.length);
                    float orbitFreq = 0.3f + 0.15f * (i % 7);
                    float angleTotal = baseAngle + current * orbitFreq * orbitSpeedScale;
                    float radius = baseRadius; // однаковий радіус
                    float x = (float)Math.cos(angleTotal) * radius;
                    float z = (float)Math.sin(angleTotal) * radius;
                    float spinFreq = 0.6f + 0.25f * (i % 5);
                    float spin = current * spinFreq;

                    glUniform1i(unlitLoc, 1);
                    glUniform1i(emissiveLoc, 1);

                    Matrix4f m = new Matrix4f()
                            .translate(x, 0.0f, z)
                            .rotateY(-angleTotal) // обличчям до центру
                            .rotateX(bottleOrientX[i])
                            .rotateZ(bottleOrientZ[i])
                            .scale(bottleScale[i]);

                    // Обертання навколо власної вертикальної осі (після орієнтації вона тепер Y)
                    m=switch (bottleSpinAxis[i]) {
                        case 0 -> m.rotateY(spin);
                        case 1 -> m.rotateX(spin);
                        case 2 -> m.rotateZ(spin);
                        default -> m;
                    };

                    glUniformMatrix4fv(modelLoc, false, m.get(fb));
                    bottles[i].render();
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
        boolean up = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
        boolean down = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
        boolean shift = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
        camera.processKeyboard(forward, back, left, right, up, down, shift, deltaTime);

        if (glfwGetKey(window, GLFW_KEY_EQUAL) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_KP_ADD) == GLFW_PRESS) {
            orbitSpeedScale += 0.8f * 0.01f;
        }
        if (glfwGetKey(window, GLFW_KEY_MINUS) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_KP_SUBTRACT) == GLFW_PRESS) {
            orbitSpeedScale -= 0.8f * 0.01f;
        }
        if (orbitSpeedScale < 0.05f) orbitSpeedScale = 0.05f;
        if (orbitSpeedScale > 1000.0f) orbitSpeedScale = 1000.0f;

        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) glfwSetWindowShouldClose(window, true);
    }

    private void cleanup() {
        if (bottles != null) for (Model m : bottles) if (m != null) m.delete();
        if (cyborgModel != null) cyborgModel.delete();
        shader.delete();
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
