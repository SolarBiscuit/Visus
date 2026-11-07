// Ported from RatOptimization's oculus_shaders module (MIT, Copyright (c) 2026 SolarBiscuit) into the Visus
// compile service (architecture §4.4, §5 L2). Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.mixin.compile;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import dev.visus.compile.ShaderBuild;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Link site of every ShaderInstance (Oculus' ExtendedShader/FallbackShader included): glLinkProgram followed by the
 * link status query, which waits for the driver's whole compile. Outside an Oculus build this only checks two fields.
 */
@Mixin(ProgramManager.class)
public abstract class ProgramManagerLinkMixin {

    @Inject(method = "linkShader", at = @At("HEAD"))
    private static void visus$linkStart(Shader shader, CallbackInfo ci) {
        ShaderBuild.linkStart();
    }

    /** parallel_contexts warm-up only: copy the sources to the compiler contexts, release this instance later, stop. */
    @Inject(method = "linkShader", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;glLinkProgram(I)V"))
    private static void visus$warmCapture(Shader shader, CallbackInfo ci) {
        if (!ShaderBuild.isCapturing()) {
            return;
        }
        visus$releaseLater(shader);
        ShaderBuild.captureBeforeLink(shader.getId(),
                shader instanceof ShaderInstance instance ? instance.getName() : "program " + shader.getId());
    }

    /** Warm-up only: keep the link running in the driver, release what this half-built instance owns later, stop. */
    @Inject(method = "linkShader", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;glGetProgrami(II)I"))
    private static void visus$warmAbort(Shader shader, CallbackInfo ci) {
        if (!ShaderBuild.isWarming()) {
            return;
        }
        visus$releaseLater(shader);
        ShaderBuild.linkIssued(shader.getId());
    }

    @Inject(method = "linkShader", at = @At("RETURN"))
    private static void visus$linkEnd(Shader shader, CallbackInfo ci) {
        ShaderBuild.linkEnd(shader instanceof ShaderInstance instance ? instance.getName() : "program " + shader.getId(),
                shader.getId());
    }

    @Unique
    private static void visus$releaseLater(Shader shader) {
        if (shader instanceof ShaderInstance instance) {
            ShaderBuild.releaseLater(instance.getVertexProgram());
            ShaderBuild.releaseLater(instance.getFragmentProgram());
            ShaderBuild.releaseLater(((ShaderInstanceUniformsAccessor) instance).visus$uniforms());
            if (instance instanceof ExtendedShader extended) {
                ShaderBuild.releaseLater(extended.getGeometry());
                ShaderBuild.releaseLater(extended.getTessControl());
                ShaderBuild.releaseLater(extended.getTessEval());
            }
        }
    }
}
