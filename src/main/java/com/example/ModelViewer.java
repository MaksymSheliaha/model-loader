package com.example;
import com.example.graphics.PointLightShadowMap;

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

    private ShaderProgram skyboxShader;
    private Skybox skybox;

    private Model[] bottles;
    private float[] bottleScale;
    private float[] bottleOrientX;
    private float[] bottleOrientZ;
    private int[] bottleSpinAxis; // 0=Y, 1=X, 2=Z
    private float[] bottleOrbitAngle;

    // Center model
    private Model cyborgModel;
    private float cyborgScale = 1.0f;

    private float lastX = width / 2f;
    private float lastY = height / 2f;
    private boolean firstMouse = true;

    private float deltaTime;
    private float lastFrame;

    private float orbitSpeedScale = 1.0f;

    private float minRadius, maxRadius;
    private float currentRadius;
    private final float speedMin = 0.2f;
    private final float speedMax = 5f;
    private final float tiltSpeedMin = 8f;
    private final float tiltSpeedMax = 20f;
    private final float rotationSpeedMin = 15f;
    private final float rotationSpeedMax = 25f;
    private final float absorbSpeedMin = 20f;
    private final float absorbSpeedMax = 25f;
    // Tilt control
    private float tiltPhase = (float)Math.PI/2.f;
    private static final float MAX_TILT = (float)(Math.PI / 4.0);
    private static final float TILT_SPEED_GAIN = 0.5f;

    // Cyborg rotation state (to avoid jerks)
    private float cyborgAngle = 0.0f;
    private float cyborgOmega = 0.0f;
    private final float cyborgOmegaMax = 3.0f;

    private boolean absorbed = false;
    private float reflectStrength = 0.0f;
    private Texture cyborgAlbedoTex;
    private Texture cyborgAltTex;

    private ShaderProgram depthCubeShader;
    private PointLightShadowMap[] shadowMaps;
    private float shadowFarPlane = 50f;

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
        // After shader created, set sampler units
        glUseProgram(shader.id());
        glUniform1i(shader.getUniformLocation("uTexture"), 0);
        glUniform1i(shader.getUniformLocation("uEnvMap"), 1);

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
        cyborgScale *= 5f;

        Vector3f cmin = cyborgModel.getBoundsMin();
        Vector3f cmax = cyborgModel.getBoundsMax();
        Vector3f size = new Vector3f(cmax).sub(cmin).mul(cyborgScale);
        float cyborgRadius = size.length() * 0.5f;
        minRadius = cyborgRadius;
        maxRadius = 5.0f * cyborgRadius;
        currentRadius = maxRadius;

        cyborgAltTex = new Texture("model/cyborg/cyborg_normal.png");
        cyborgAlbedoTex = new Texture("model/cyborg/cyborg_diffuse.png");

        String[][] bottleRes = new String[][]{
                {"model/beer-v1/beer.obj", "model/beer-v1/14043_16_oz._Beer_Bottle_diff.jpg"},
                //{"model/bud/bud.obj", "model/bud/BUD2.jpeg"},
                //{"model/beer-v2/beer.obj", "model/beer-v2/14043_16_oz._Beer_Bottle_diff_final.jpg"},
                //{"model/heineken/heineken.obj", "model/heineken/material_baseColor.png"},
                //{"model/corona/Corona.obj", "model/corona/BotellaText.jpg"},
                //{"model/stella/stella-artois.obj", "model/stella/STELLAARTOIS2.png"},
                //{"model/beer-v1/beer.obj", "model/beer-v1/14043_16_oz._Beer_Bottle_diff.jpg"},
                //{"model/bud/bud.obj", "model/bud/BUD2.jpeg"},
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
        bottleOrbitAngle = new float[bottleRes.length];

        for (int i = 0; i < bottleRes.length; i++) {
            bottles[i] = ModelLoader.loadObjWithTexture(bottleRes[i][0], bottleRes[i][1]);
            float extent = Math.max(1e-6f, bottles[i].getMaxExtent());
            bottleScale[i] = targetSize / extent;

            bottleOrbitAngle[i] = (float) (2.0 * Math.PI * i / bottleRes.length);

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
                bottleOrientZ[i] = (float)Math.toRadians(flip ? -90.0 : 90.0);
                bottleOrientX[i] = 0.0f;
                bottleSpinAxis[i] = 1;
            } else if (zLongest) {
                bottleOrientX[i] = (float)Math.toRadians(flip ? 90.0 : -90.0);
                bottleOrientZ[i] = 0.0f;
                bottleSpinAxis[i] = 2;
            } else {
                bottleOrientX[i] = 0.0f;
                bottleOrientZ[i] = 0.0f;
                bottleSpinAxis[i] = 0;
            }
        }

        depthCubeShader = new ShaderProgram("shaders/depth_cube.vert", "shaders/depth_cube.frag");
        // Shadow maps array sized to bottle count (capped later)
        shadowMaps = new PointLightShadowMap[16];
        for (int i = 0; i < shadowMaps.length; i++) {
            shadowMaps[i] = new PointLightShadowMap(512, shadowFarPlane);
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

            glDepthFunc(GL_LEQUAL);
            skyboxShader.use();
            try (var stack = stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(60), (float)width/height, 0.1f, 100f);
                int projLoc = skyboxShader.getUniformLocation("uProjection");
                glUniformMatrix4fv(projLoc, false, projection.get(fb));
                Matrix4f view = camera.getViewMatrix();
                view.m30(0).m31(0).m32(0);
                int viewLoc = skyboxShader.getUniformLocation("uViewRot");
                glUniformMatrix4fv(viewLoc, false, view.get(fb));
            }
            skybox.bindTexture(0);
            skybox.render();
            glDepthFunc(GL_LESS);

            // Precompute radius, tilt and light positions once per frame
            // Update currentRadius smoothly
            float targetRadius;
            if (orbitSpeedScale < absorbSpeedMin) {
                float t = (orbitSpeedScale - speedMin) / (speedMax - speedMin);
                if (t < 0f) t = 0f; if (t > 1f) t = 1f;
                targetRadius = maxRadius + (minRadius - maxRadius) * t;
            } else {
                float t = (orbitSpeedScale - absorbSpeedMin) / (absorbSpeedMax - absorbSpeedMin);
                if (t < 0f) t = 0f; if (t > 1f) t = 1f;
                targetRadius = minRadius + (0 - minRadius) * t;
            }
            float smooth = 1f - (float) Math.exp(-5f * Math.max(0.0001f, deltaTime));
            currentRadius += (targetRadius - currentRadius) * smooth;

            // Update tilt phase and compute tilt
            if (orbitSpeedScale >= tiltSpeedMin) {
                float omega = (orbitSpeedScale < tiltSpeedMax)
                        ? TILT_SPEED_GAIN * (orbitSpeedScale - tiltSpeedMin)
                        : TILT_SPEED_GAIN * (tiltSpeedMax - tiltSpeedMin);
                tiltPhase += omega * deltaTime;
                if (tiltPhase > Math.PI) tiltPhase -= (float)(Math.PI * 2);
            }
            float tilt = (float)Math.cos(tiltPhase) * MAX_TILT;
            float sTilt = (float)Math.sin(tilt);
            float cTilt = (float)Math.cos(tilt);

            // Cyborg vertical center
            Vector3f cyborgMinForLights = cyborgModel.getBoundsMin();
            Vector3f cyborgMaxForLights = cyborgModel.getBoundsMax();
            float cyborgMidYForLights = (cyborgMinForLights.y + cyborgMaxForLights.y) * 0.5f * cyborgScale;

            final int MAX_LIGHTS = 16;
            int totalLights = Math.min(bottles.length, MAX_LIGHTS);
            float[] angleTotals = new float[totalLights];
            Vector3f[] precomputedLightPos = new Vector3f[totalLights];
            for (int i = 0; i < totalLights; i++) {
                // Advance orbit angle
                float orbitFreq = 0.3f + 0.15f * (i % 5);
                float orbitOmega = orbitFreq * orbitSpeedScale;
                bottleOrbitAngle[i] += orbitOmega * deltaTime;
                angleTotals[i] = bottleOrbitAngle[i];
                // Compute light position
                float xBase = (float)Math.cos(angleTotals[i]) * currentRadius;
                float zBase = (float)Math.sin(angleTotals[i]) * currentRadius;
                float yOff = xBase * sTilt;
                float xPos = xBase * cTilt;
                float yPos = cyborgMidYForLights + yOff;
                float zPos = zBase;
                precomputedLightPos[i] = new Vector3f(xPos, yPos, zPos);
            }

            // Build shadow maps using precomputed positions
            if (!absorbed) {
                for (int li = 0; li < totalLights; li++) {
                    Vector3f lightPos = precomputedLightPos[li];
                    PointLightShadowMap sm = shadowMaps[li];
                    sm.bindForWrite();
                    glClear(GL_DEPTH_BUFFER_BIT);
                    depthCubeShader.use();
                    Matrix4f[] mats = PointLightShadowMap.buildLightSpaceMatrices(lightPos, 0.1f, shadowFarPlane);
                    try (var stack = stackPush()) {
                        FloatBuffer fb = stack.mallocFloat(16);
                        int modelLoc = depthCubeShader.getUniformLocation("uModel");
                        int lightSpaceLoc = depthCubeShader.getUniformLocation("uLightSpace");
                        int lightPosLoc = depthCubeShader.getUniformLocation("uLightPos");
                        int farPlaneLoc = depthCubeShader.getUniformLocation("uFarPlane");
                        glUniform3f(lightPosLoc, lightPos.x, lightPos.y, lightPos.z);
                        glUniform1f(farPlaneLoc, shadowFarPlane);
                        Matrix4f cybM = new Matrix4f().scale(cyborgScale).rotateY(cyborgAngle);
                        glUniformMatrix4fv(modelLoc, false, cybM.get(fb));
                        for (int face = 0; face < 6; face++) {
                            glUniformMatrix4fv(lightSpaceLoc, false, mats[face].get(fb));
                            cyborgModel.render();
                        }
                    }
                    sm.unbind();
                }
                glViewport(0, 0, width, height);
            }

            // Main pass
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
            int uReflectLoc = shader.getUniformLocation("uReflect");
            int uReflectStrengthLoc = shader.getUniformLocation("uReflectStrength");
            int uGlowLoc = shader.getUniformLocation("uGlow");
            int shadowEnabledLoc = shader.getUniformLocation("uShadowEnabled");
            int shadowFarLoc = shader.getUniformLocation("uShadowFarPlane");
            glUniform1i(shadowEnabledLoc, absorbed ? 0 : 1);
            glUniform1f(shadowFarLoc, shadowFarPlane);
            if (!absorbed) {
                int bindCount = Math.min(totalLights, 12); // limited by shader samplers
                for (int i = 0; i < bindCount; i++) {
                    glActiveTexture(GL_TEXTURE2 + i);
                    glBindTexture(GL_TEXTURE_CUBE_MAP, shadowMaps[i].depthCubeTex);
                    int loc = glGetUniformLocation(shader.id(), "uShadowMap" + i);
                    glUniform1i(loc, 2 + i);
                }
            }

            try (var stack = stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(60), (float) width / height, 0.1f, 100f);
                glUniformMatrix4fv(projLoc, false, projection.get(fb));
                Matrix4f view = camera.getViewMatrix();
                glUniformMatrix4fv(viewLoc, false, view.get(fb));

                glUniform3f(viewPosLoc, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
                glUniform1f(ambientLoc, 0.03f);
                glUniform1f(specLoc, 0.7f);
                glUniform1f(shinLoc, 48.0f);

                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
                skybox.bindTexture(1);

                if(!absorbed) {
                    glUniform1i(lightCountLoc, totalLights);
                    for (int i = 0; i < totalLights; i++) {
                        Vector3f lp = precomputedLightPos[i];
                        int uPosLoc = glGetUniformLocation(shader.id(), "uLightPos[" + i + "]");
                        int uColLoc = glGetUniformLocation(shader.id(), "uLightColor[" + i + "]");
                        glUniform3f(uPosLoc, lp.x, lp.y, lp.z);
                        glUniform3f(uColLoc, 1.0f, 0.95f, 0.85f);
                    }
                } else {
                    glUniform1i(lightCountLoc, 0);
                }

                // Reflect/glow toggles
                if (!absorbed && orbitSpeedScale >= absorbSpeedMax && currentRadius <= Math.max(0.1f, 0.02f * maxRadius)) {
                    absorbed = true;
                    glUniform1i(lightCountLoc, 0);
                    reflectStrength = 0.0f;
                    if (cyborgAltTex != null) cyborgModel.setOverrideTexture(cyborgAltTex);
                }
                if (absorbed && orbitSpeedScale <= absorbSpeedMax && currentRadius >= Math.max(0.1f, 0.02f * maxRadius)) {
                    absorbed = false;
                    reflectStrength = 0.0f;
                    if (cyborgAltTex != null) cyborgModel.setOverrideTexture(cyborgAlbedoTex);
                }
                if (absorbed) {
                    reflectStrength += (1.0f - reflectStrength) * (1f - (float) Math.exp(-2.5f * Math.max(0.0001f, deltaTime)));
                    glUniform1i(uReflectLoc, 1);
                    glUniform1f(uReflectStrengthLoc, Math.min(1.0f, reflectStrength));
                    glUniform1f(uGlowLoc, 0.15f);
                } else {
                    glUniform1i(uReflectLoc, 0);
                    glUniform1f(uReflectStrengthLoc, 0.0f);
                    glUniform1f(uGlowLoc, 0.0f);
                }

                // Render cyborg
                glUniform1i(unlitLoc, 0);
                glUniform1i(emissiveLoc, 0);
                Matrix4f cybM = new Matrix4f().scale(cyborgScale);
                float sRot = (orbitSpeedScale - rotationSpeedMin) / (rotationSpeedMax - rotationSpeedMin);
                if (sRot < 0f) sRot = 0f; if (sRot > 1f) sRot = 1f;
                sRot = sRot * sRot * (3f - 2f * sRot);
                float targetOmega = cyborgOmegaMax * sRot;
                float omegaSmooth = 1f - (float) Math.exp(-4f * Math.max(0.0001f, deltaTime));
                cyborgOmega += (targetOmega - cyborgOmega) * omegaSmooth;
                cyborgAngle += cyborgOmega * deltaTime;
                if (cyborgAngle > Math.PI * 2) cyborgAngle -= (float) (Math.PI * 2);
                if (cyborgAngle < -Math.PI * 2) cyborgAngle += (float) (Math.PI * 2);
                cybM.rotateY(cyborgAngle);
                glUniformMatrix4fv(modelLoc, false, cybM.get(fb));
                cyborgModel.render();

                // Render bottles using precomputed positions and angles
                if(!absorbed) {
                    for (int i = 0; i < bottles.length; i++) {
                        float spinFreq = 0.8f + 0.25f * (i % 5);
                        float spin = current * spinFreq;

                        Vector3f lp = (i < totalLights) ? precomputedLightPos[i] : null;
                        float angleTotal = (i < totalLights) ? angleTotals[i] : bottleOrbitAngle[i];
                        float xPos = (lp != null) ? lp.x : (float)Math.cos(angleTotal) * currentRadius * cTilt; // fallback
                        float zPos = (lp != null) ? lp.z : (float)Math.sin(angleTotal) * currentRadius;
                        float yPos = (lp != null) ? lp.y : ((float)Math.cos(angleTotal) * currentRadius) * sTilt + cyborgMidYForLights;

                        glUniform1i(unlitLoc, 1);
                        glUniform1i(emissiveLoc, 1);

                        Matrix4f m = new Matrix4f()
                                .translate(xPos, yPos, zPos)
                                .rotateY(-angleTotal)
                                .rotateX(bottleOrientX[i])
                                .rotateZ(bottleOrientZ[i])
                                .scale(bottleScale[i]);
                        switch (bottleSpinAxis[i]) {
                            case 0 -> m.rotateY(spin);
                            case 1 -> m.rotateX(spin);
                            case 2 -> m.rotateZ(spin);
                        }

                        glUniformMatrix4fv(modelLoc, false, m.get(fb));
                        bottles[i].render();
                    }
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
        if (orbitSpeedScale < -20f) orbitSpeedScale = -20f;
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
        if (cyborgAltTex != null) cyborgAltTex.delete();
        if (depthCubeShader != null) depthCubeShader.delete();
        if (shadowMaps != null) {
            for (PointLightShadowMap sm : shadowMaps) if (sm != null) sm.delete();
        }
    }
}
