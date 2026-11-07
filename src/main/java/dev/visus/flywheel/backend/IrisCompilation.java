// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.backend;

import dev.engine_room.flywheel.backend.compile.core.Compilation;
import dev.engine_room.flywheel.backend.compile.core.ShaderResult;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import dev.visus.flywheel.mixin.flw.FlwCompilationAccessor;

public class IrisCompilation extends Compilation {
    private final FlwCompilationAccessor compilationAccessor;
    public IrisCompilation()
    {
        compilationAccessor=(FlwCompilationAccessor)this;
    }

    @Override
    public ShaderResult compile(ShaderType shaderType, String name) {
        String source = this.compilationAccessor.getFullSource().toString();
        String shaderName = name + "." + shaderType.extension;
        FlwCompilationAccessor.invokeDumpSource(source, shaderName);
        return ShaderResult.success(new IntermediateGlShader(source, shaderType, name), "");
    }
}
