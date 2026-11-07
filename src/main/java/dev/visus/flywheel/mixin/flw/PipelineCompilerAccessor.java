// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.flw;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.compile.component.UberShaderComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PipelineCompiler.class, remap = false)
public interface PipelineCompilerAccessor {

    @Accessor(value = "FOG")
    public static UberShaderComponent GetFOG(){
        throw new AssertionError();
    }

    @Accessor(value = "CUTOUT")
    public static UberShaderComponent GetCUTOUT(){
        throw new AssertionError();
    }
}
