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
    // Skybox resources
    private ShaderProgram skyboxShader;
    private Skybox skybox;

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
    // Radius control based on cyborg size
    private float minRadius, maxRadius; // min = 1x cyborg radius, max = 5x cyborg radius
    private float currentRadius;        // smoothed radius used for orbiting
    private final float speedMin = 0.2f; // speed where radius starts to shrink
    private final float speedMax = 3.0f; // speed where radius reaches min
    // Tilt control
    private float tiltPhase = 0f; // evolves over time when speed >= threshold
    private static final float MAX_TILT = (float)(Math.PI / 4.0); // [-pi/4, pi/4]
    private static final float TILT_SPEED_THRESHOLD = 8.0f; // start tilting at this speed
    private static final float TILT_SPEED_GAIN = 0.5f; // rad/s per unit over threshold
    private static final float TILT_SPEED_MAX = 6.0f; // clamp to avoid too fast

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
        glfwSetFramebufferSizeCallback(window, (window, width, height)->{
            if (width > 0 && height > 0 && (this.width != width || this.height != height)) {
                this.width = width;
                this.height = height;
            }
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

        // Load skybox cubemap & shader
        skyboxShader = new ShaderProgram("shaders/skybox.vert", "shaders/skybox.frag");
        glUseProgram(skyboxShader.id());
        int skyboxLoc = skyboxShader.getUniformLocation("uSkybox");
        glUniform1i(skyboxLoc, 0);
        CubeMapTexture cubeMap = new CubeMapTexture("environment/cubemap/hitori");
        skybox = new Skybox(cubeMap);

        // Center model
        cyborgModel = ModelLoader.loadObjWithTexture("model/cyborg/cyborg.obj", "model/cyborg/cyborg_diffuse.png");
        float targetSize = 2.0f;
        cyborgScale = targetSize / Math.max(1e-6f, cyborgModel.getMaxExtent());
        cyborgScale *= 5f; // трохи більший кіборг

        // Compute cyborg bounding sphere radius in world space
        Vector3f cmin = cyborgModel.getBoundsMin();
        Vector3f cmax = cyborgModel.getBoundsMax();
        Vector3f size = new Vector3f(cmax).sub(cmin).mul(cyborgScale);
        float cyborgRadius = size.length() * 0.5f; // half of diagonal length
        minRadius = cyborgRadius;
        maxRadius = 5.0f * cyborgRadius;
        currentRadius = maxRadius; // start wide

        // Six distinct bottle models/textures from resources/model
        String[][] bottleRes = new String[][]{
                {"model/beer-v1/beer.obj", "model/beer-v1/14043_16_oz._Beer_Bottle_diff.jpg"},
                {"model/bud/bud.obj", "model/bud/BUD2.jpeg"},
                {"model/beer-v2/beer.obj", "model/beer-v2/14043_16_oz._Beer_Bottle_diff_final.jpg"},
                {"model/heineken/heineken.obj", "model/heineken/material_baseColor.png"},
                {"model/corona/Corona.obj", "model/corona/BotellaText.jpg"},
                {"model/stella/stella-artois.obj", "model/stella/STELLAARTOIS2.png"},
                {"model/beer-v1/beer.obj", "model/beer-v1/14043_16_oz._Beer_Bottle_diff.jpg"},
                {"model/bud/bud.obj", "model/bud/BUD2.jpeg"},
                {"model/beer-v2/beer.obj", "model/beer-v2/14043_16_oz._Beer_Bottle_diff_final.jpg"},
                {"model/heineken/heineken.obj", "model/heineken/material_baseColor.png"},
                {"model/corona/Corona.obj", "model/corona/BotellaText.jpg"},
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

            // Render skybox first (depth = far plane) or last with LEQUAL; choose first for simplicity
            glDepthFunc(GL_LEQUAL);
            skyboxShader.use();
            try (var stack = stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(60), (float)width/height, 0.1f, 100f);
                int projLoc = skyboxShader.getUniformLocation("uProjection");
                glUniformMatrix4fv(projLoc, false, projection.get(fb));
                // View matrix without translation
                Matrix4f view = camera.getViewMatrix();
                view.m30(0).m31(0).m32(0); // remove translation component
                int viewLoc = skyboxShader.getUniformLocation("uViewRot");
                glUniformMatrix4fv(viewLoc, false, view.get(fb));
            }
            skybox.bindTexture(0);
            skybox.render();
            glDepthFunc(GL_LESS);

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

                // Update target orbit radius based on orbit speed: higher speed -> smaller radius
                float t = (orbitSpeedScale - speedMin) / (speedMax - speedMin);
                if (t < 0f) t = 0f; if (t > 1f) t = 1f;
                float targetRadius = maxRadius + (minRadius - maxRadius) * t; // mix(max,min,t)
                // Smooth towards target (deltaTime-based smoothing)
                float smooth = 1f - (float)Math.exp(-5f * Math.max(0.0001f, deltaTime));
                currentRadius += (targetRadius - currentRadius) * smooth;

                // Update tilt phase if speed above threshold
                if (orbitSpeedScale >= TILT_SPEED_THRESHOLD) {
                    float omega = TILT_SPEED_GAIN * (orbitSpeedScale - TILT_SPEED_THRESHOLD);
                    if (omega > TILT_SPEED_MAX) omega = TILT_SPEED_MAX;
                    tiltPhase += omega * deltaTime;
                }
                float tilt = (float)Math.cos(tiltPhase) * MAX_TILT; // [-pi/4..pi/4]
                float sTilt = (float)Math.sin(tilt);
                float cTilt = (float)Math.cos(tilt);

                int count = bottles.length; // lights near bottles
                glUniform1i(lightCountLoc, count);
                for (int i = 0; i < count; i++) {
                    float baseAngle = (float)(2.0 * Math.PI * i / count);
                    float orbitFreq = 0.3f + 0.15f * (i % 7);
                    float angleTotal = baseAngle + current * orbitFreq * orbitSpeedScale;
                    float xBase = (float)Math.cos(angleTotal) * currentRadius;
                    float zBase = (float)Math.sin(angleTotal) * currentRadius;
                    // Rotate around X by tilt: (x, y=0, z) -> (x, y'=-z*sin, z'=z*cos)
                    float xPos =  xBase * cTilt;
                    float yPos =  xBase * sTilt;
                    float zPos =  zBase;
                    int uPosLoc = glGetUniformLocation(shader.id(), "uLightPos[" + i + "]");
                    int uColLoc = glGetUniformLocation(shader.id(), "uLightColor[" + i + "]");
                    glUniform3f(uPosLoc, xPos, yPos, zPos);
                    glUniform3f(uColLoc, 1.0f, 0.95f, 0.85f);
                }

                // Cyborg (lit)
                glUniform1i(unlitLoc, 0);
                glUniform1i(emissiveLoc, 0);
                Matrix4f cybM = new Matrix4f().scale(cyborgScale);
                glUniformMatrix4fv(modelLoc, false, cybM.get(fb));
                cyborgModel.render();

                // Calculate cyborg mid-height in world space
                Vector3f cyborgMin = cyborgModel.getBoundsMin();
                Vector3f cyborgMax = cyborgModel.getBoundsMax();
                float cyborgMidY = (cyborgMin.y + cyborgMax.y) * 0.5f * cyborgScale;

                // Bottles (unlit + emissive)
                for (int i = 0; i < bottles.length; i++) {
                    float baseAngle = (float)(2.0 * Math.PI * i / bottles.length);
                    float orbitFreq = 0.3f + 0.15f;// * (i % 7);
                    float angleTotal = baseAngle + current * orbitFreq * orbitSpeedScale;
                    float xBase = (float)Math.cos(angleTotal) * currentRadius;
                    float zBase = (float)Math.sin(angleTotal) * currentRadius;
                    float yOff = xBase * sTilt;
                    float xPos =  xBase * cTilt;
                    float zPos =  zBase;
                    float spinFreq = 0.6f + 0.25f * (i % 5);
                    float spin = current * spinFreq;

                    glUniform1i(unlitLoc, 1);
                    glUniform1i(emissiveLoc, 1);

                    Matrix4f m = new Matrix4f()
                            .translate(xPos, cyborgMidY + yOff, zPos)
                            .rotateY(-angleTotal) // face to center (approx)
                            .rotateX(bottleOrientX[i])
                            .rotateZ(bottleOrientZ[i])
                            .scale(bottleScale[i]);

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
            orbitSpeedScale += 0.8f * 0.1f;
            System.out.println(orbitSpeedScale);
        }
        if (glfwGetKey(window, GLFW_KEY_MINUS) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_KP_SUBTRACT) == GLFW_PRESS) {
            orbitSpeedScale -= 0.8f * 0.1f;
            System.out.println(orbitSpeedScale);
        }
        if (orbitSpeedScale < -1000f) orbitSpeedScale = -1000f;
        if (orbitSpeedScale > 1000.0f) orbitSpeedScale = 1000.0f;

        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) glfwSetWindowShouldClose(window, true);
    }

    private void cleanup() {
        if (bottles != null) for (Model m : bottles) if (m != null) m.delete();
        if (cyborgModel != null) cyborgModel.delete();
        shader.delete();
        if (skybox != null) skybox.delete();
        if (skyboxShader != null) skyboxShader.delete();
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
