// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.iris;

import net.irisshaders.iris.pipeline.PipelineManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.visus.flywheel.backend.IrisPipelineCompiler;

@Mixin(value = PipelineManager.class)
public class MixinPipelineManager {

    @Inject(method = "destroyPipeline", at = @At("TAIL"), remap = false)
    public void irisflw$destroyPipeline(CallbackInfo ci) {
        IrisPipelineCompiler.deleteAll();
    }
}
