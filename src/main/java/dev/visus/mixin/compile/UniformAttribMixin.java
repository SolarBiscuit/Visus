// Ported from RatOptimization's oculus_shaders module (MIT, Copyright (c) 2026 SolarBiscuit) into the Visus
// compile service (architecture §4.4, §5 L2). Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.mixin.compile;

import com.mojang.blaze3d.shaders.Uniform;
import dev.visus.compile.ShaderBuild;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ShaderInstance binds its vertex attributes here before the link. The parallel_contexts warm-up copies those bindings
 * to the compiler contexts so their link matches the real one. Outside a warm-up this only checks two fields.
 */
@Mixin(Uniform.class)
public abstract class UniformAttribMixin {

    @Inject(method = "glBindAttribLocation", at = @At("HEAD"))
    private static void visus$recordAttrib(int program, int index, CharSequence name, CallbackInfo ci) {
        ShaderBuild.attribBound(program, index, name);
    }
}
