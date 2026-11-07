// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.flw;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.visus.flywheel.backend.IrisPipelineCompiler;

@Mixin(value = PipelineCompiler.class, remap = false)
public class MixinPipelineCompiler {

    @Inject(method = "deleteAll", at = @At("TAIL"))
    private static void irisflw$deleteAll(CallbackInfo ci) {
        IrisPipelineCompiler.deleteAll();
    }
}
