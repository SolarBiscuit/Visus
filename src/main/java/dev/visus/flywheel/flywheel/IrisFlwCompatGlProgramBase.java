// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.flywheel;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public abstract class IrisFlwCompatGlProgramBase extends GlProgram {
    public IrisFlwCompatGlProgramBase(int handle) {
        super(handle);
    }

    /**
     * Visus V-18: the GL program behind this wrapper is an {@code ExtendedShader} made by the Iris pipeline's own
     * {@code createShader}/{@code createShadowShader}, so the pipeline owns it and deletes it when it is destroyed.
     * irisflw also deleted it here (from its {@code PipelineManager.destroyPipeline} hook, after the pipeline had),
     * which raised GL_INVALID_VALUE (0x3F6 on AMD) on every pipeline destroy.
     */
    @Override
    protected void deleteInternal(int handle) {
    }

    public abstract void bind();

    public abstract void clear();

    public abstract void setProjectionMatrix(Matrix4f projectionMatrix);

    public abstract void setModelViewMatrix(Matrix4f modelView);

    public static class Invalid extends IrisFlwCompatGlProgramBase {

        public static final Invalid INSTANCE = new Invalid();

        public Invalid() {
            super(0);
        }

        @Override
        public void bind() {

        }

        @Override
        public void clear() {

        }

        @Override
        public void setProjectionMatrix(Matrix4f projectionMatrix) {

        }

        @Override
        public void setModelViewMatrix(Matrix4f modelView) {

        }

        @Override
        public void delete() {
            // No-op for invalid program
        }
    }
}
