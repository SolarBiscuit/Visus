// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.flw;

import dev.engine_room.flywheel.backend.compile.core.Compilation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Compilation.class)
public interface FlwCompilationAccessor {

    @Accessor
    StringBuilder getGeneratedSource();

    @Accessor
    StringBuilder getFullSource();

    @Invoker(value = "dumpSource")
    static void invokeDumpSource(String source, String fileName)
    {

    }
}
