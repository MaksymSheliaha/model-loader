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
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class ModelViewer {
    private long window;
    private int width = 1280;
    private int height = 720;

    private ShaderProgram shader;
    private ShaderProgram depthCubeShader;
    private Camera camera = new Camera();

    private Model coronaModel;
    private Model cyborgModel;

    // Auto scale factors so both models look comparable in size
    private float coronaScale = 1.0f;
    private float cyborgScale = 1.0f;

    // Orientation correction angles (radians) for corona to stand vertical
    private float coronaOrientX = 0.0f;
    private float coronaOrientZ = 0.0f;

    // Shadow cubemap
    private int depthCubeFBO;
    private int depthCubeTex;
    private int shadowSize = 1024;
    private float shadowFar = 25.0f;

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

    private void createDepthCubemap() {
        depthCubeFBO = glGenFramebuffers();
        depthCubeTex = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubeTex);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_DEPTH_COMPONENT24, shadowSize, shadowSize, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0);
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindFramebuffer(GL_FRAMEBUFFER, depthCubeFBO);
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthCubeTex, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Depth cubemap FBO is not complete");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void initScene() {
        shader = new ShaderProgram("shaders/basic.vert", "shaders/basic.frag");
        // depth cube uses geometry shader to expand to 6 faces
        depthCubeShader = new ShaderProgram("shaders/depthCube.vert", "shaders/depthCube.geom", "shaders/depthCube.frag");
        int texLoc = shader.getUniformLocation("uTexture");
        glUseProgram(shader.id());
        glUniform1i(texLoc, 0);
        int cubeLoc = shader.getUniformLocation("uDepthCube");
        glUniform1i(cubeLoc, 1);

        // Load models using Assimp + STB
        coronaModel = ModelLoader.loadObjWithTexture("model/corona/Corona.obj", "model/corona/BotellaText.jpg");
        cyborgModel = ModelLoader.loadObjWithTexture("model/cyborg/cyborg.obj", "model/cyborg/cyborg_diffuse.png");

        // Compute per-model auto-scale so the max extent maps to a target size
        float targetSize = 2.0f; // world units for the largest side after scaling
        float coronaExtent = Math.max(1e-6f, coronaModel.getMaxExtent());
        float cyborgExtent = Math.max(1e-6f, cyborgModel.getMaxExtent());
        coronaScale = targetSize / coronaExtent;
        cyborgScale = targetSize / cyborgExtent;
        cyborgScale *= 1.3f; // make cyborg a bit larger

        // Compute orientation correction for corona: align its longest axis to Y (vertical)
        Vector3f min = coronaModel.getBoundsMin();
        Vector3f max = coronaModel.getBoundsMax();
        float dx = Math.abs(max.x - min.x);
        float dy = Math.abs(max.y - min.y);
        float dz = Math.abs(max.z - min.z);
        if (dx >= dy && dx >= dz) {
            coronaOrientZ = (float) Math.toRadians(90.0);
            coronaOrientX = 0.0f;
        } else if (dz >= dx && dz >= dy) {
            coronaOrientX = (float) Math.toRadians(-90.0);
            coronaOrientZ = 0.0f;
        } else {
            coronaOrientX = 0.0f;
            coronaOrientZ = 0.0f;
        }

        createDepthCubemap();
    }

    private Matrix4f[] computeShadowMatrices(Vector3f lightPos) {
        float near = 0.1f;
        float far = shadowFar;
        Matrix4f proj = new Matrix4f().perspective((float)Math.toRadians(90.0), 1.0f, near, far);
        Vector3f upX = new Vector3f(0, -1, 0);
        Vector3f upY = new Vector3f(0, 0, 1);
        Vector3f upZ = new Vector3f(0, -1, 0);
        return new Matrix4f[]{
                new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add( 1, 0, 0), upX)), // +X
                new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add(-1, 0, 0), upX)), // -X
                new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add( 0, 1, 0), upY)), // +Y
                new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add( 0,-1, 0), upY)), // -Y
                new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add( 0, 0, 1), upZ)), // +Z
                new Matrix4f(proj).mul(new Matrix4f().lookAt(lightPos, new Vector3f(lightPos).add( 0, 0,-1), upZ))  // -Z
        };
    }

    private void renderDepthCube(Vector3f lightPos, float time) {
        glViewport(0, 0, shadowSize, shadowSize);
        glBindFramebuffer(GL_FRAMEBUFFER, depthCubeFBO);
        glClear(GL_DEPTH_BUFFER_BIT);

        depthCubeShader.use();
        int uFarLoc = depthCubeShader.getUniformLocation("uFar");
        int uLightPosLoc = depthCubeShader.getUniformLocation("uLightPos");
        int uModelLoc = depthCubeShader.getUniformLocation("uModel");
        int uShadowMatLoc = glGetUniformLocation(depthCubeShader.id(), "uShadowMatrices[0]");

        try (var stack = stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            glUniform1f(uFarLoc, shadowFar);
            glUniform3f(uLightPosLoc, lightPos.x, lightPos.y, lightPos.z);

            Matrix4f[] mats = computeShadowMatrices(lightPos);
            for (int i = 0; i < 6; i++) {
                glUniformMatrix4fv(uShadowMatLoc + i, false, mats[i].get(fb));
            }

            // Cyborg
            Matrix4f cybM = new Matrix4f().scale(cyborgScale);
            glUniformMatrix4fv(uModelLoc, false, cybM.get(fb));
            cyborgModel.render();

            // Corona ring (with spin)
            int count = 8;
            float baseRadius = 4.5f;
            float var = 1.2f;
            for (int i = 0; i < count; i++) {
                float angle = (float)(2.0 * Math.PI * i / count);
                float radius = baseRadius + ((i % 3) * var);
                float x = (float)Math.cos(angle) * radius;
                float z = (float)Math.sin(angle) * radius;
                float freq = 0.6f + 0.25f * (i % 5); // different frequency per bottle
                float spin = time * freq;
                Matrix4f coronaM = new Matrix4f()
                        .translate(x, 0.0f, z)
                        .rotateY(-angle)
                        .rotateX(coronaOrientX)
                        .rotateZ(coronaOrientZ)
                        .rotateZ(spin)
                        .scale(coronaScale);
                glUniformMatrix4fv(uModelLoc, false, coronaM.get(fb));
                coronaModel.render();
            }
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            float current = (float)glfwGetTime();
            deltaTime = current - lastFrame; lastFrame = current;
            processInput();

            Vector3f lightPos = new Vector3f(0.0f, 0.0f, 0.0f);
            renderDepthCube(lightPos, current);

            glViewport(0, 0, width, height);
            glClearColor(0.02f, 0.02f, 0.03f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shader.use();
            int projLoc = shader.getUniformLocation("uProjection");
            int viewLoc = shader.getUniformLocation("uView");
            int modelLoc = shader.getUniformLocation("uModel");
            int lightPosLoc = shader.getUniformLocation("uLightPos");
            int lightColLoc = shader.getUniformLocation("uLightColor");
            int viewPosLoc = shader.getUniformLocation("uViewPos");
            int ambientLoc = shader.getUniformLocation("uAmbient");
            int specLoc = shader.getUniformLocation("uSpecularStrength");
            int shinLoc = shader.getUniformLocation("uShininess");
            int emissiveLoc = shader.getUniformLocation("uEmissive");
            int farLoc = shader.getUniformLocation("uFar");

            try (var stack = stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(60), (float)width/height, 0.1f, 100f);
                glUniformMatrix4fv(projLoc, false, projection.get(fb));
                Matrix4f view = camera.getViewMatrix();
                glUniformMatrix4fv(viewLoc, false, view.get(fb));

                glUniform3f(lightPosLoc, lightPos.x, lightPos.y, lightPos.z);
                glUniform3f(lightColLoc, 1.0f, 0.95f, 0.85f);
                glUniform3f(viewPosLoc, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
                glUniform1f(ambientLoc, 0.02f);
                glUniform1f(specLoc, 0.6f);
                glUniform1f(shinLoc, 48.0f);
                glUniform1f(farLoc, shadowFar);

                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_CUBE_MAP, depthCubeTex);

                // Cyborg in the center (emissive)
                glUniform1i(emissiveLoc, 1);
                Matrix4f cybM = new Matrix4f().scale(cyborgScale);
                glUniformMatrix4fv(modelLoc, false, cybM.get(fb));
                cyborgModel.render();

                // Corona ring (with spin)
                glUniform1i(emissiveLoc, 0);
                int count = 8;
                float baseRadius = 4.5f;
                float var = 1.2f;
                for (int i = 0; i < count; i++) {
                    float angle = (float)(2.0 * Math.PI * i / count);
                    float radius = baseRadius + ((i % 3) * var);
                    float x = (float)Math.cos(angle) * radius;
                    float z = (float)Math.sin(angle) * radius;
                    float freq = 0.6f + 0.25f * (i % 5);
                    float spin = current * freq;
                    Matrix4f coronaM = new Matrix4f()
                            .translate(x, 0.0f, z)
                            .rotateY(-angle)
                            .rotateX(coronaOrientX)
                            .rotateZ(coronaOrientZ)
                            .rotateZ(spin)
                            .scale(coronaScale);
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
        depthCubeShader.delete();
        if (depthCubeFBO != 0) glDeleteFramebuffers(depthCubeFBO);
        if (depthCubeTex != 0) glDeleteTextures(depthCubeTex);
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
