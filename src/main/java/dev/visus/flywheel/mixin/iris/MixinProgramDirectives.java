// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.iris;

import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.visus.flywheel.accessors.ProgramDirectivesAccessor;

import java.util.Optional;

@Mixin(value = ProgramDirectives.class,remap = false)
public class MixinProgramDirectives implements ProgramDirectivesAccessor {
    @Unique
    private AlphaTest flwAlphaTestOverride;

    @Override
    public void setFlwAlphaTestOverride(AlphaTest alphaTest) {
        flwAlphaTestOverride = alphaTest;
    }

    @Inject(method = "getAlphaTestOverride", at = @At("HEAD"), cancellable = true)
    private void injectAlphaTestOverride(CallbackInfoReturnable<Optional<AlphaTest>> cir){
        if (flwAlphaTestOverride!=null)
            cir.setReturnValue(Optional.of(flwAlphaTestOverride));
    }
}
