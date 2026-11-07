// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.backend;

import dev.engine_room.flywheel.backend.gl.shader.GlShader;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;

public class IntermediateGlShader extends GlShader {
    private final String source;
    private final String name;

    public IntermediateGlShader(String source, ShaderType type, String name) {
        super(0, type, name);
        this.source = source;
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public String getName()
    {
        return name;
    }
}
