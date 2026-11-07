// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.iris;

import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniform;
import org.joml.Matrix3f;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public class GlUniformMcMatrix3f extends GlUniform<Matrix3f> {

    public GlUniformMcMatrix3f(int index) {
        super(index);
    }


    public void set(Matrix3f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(12);
            value.get(buf);

            GL30C.glUniformMatrix3fv(this.index, false, buf);
        }
    }
}
