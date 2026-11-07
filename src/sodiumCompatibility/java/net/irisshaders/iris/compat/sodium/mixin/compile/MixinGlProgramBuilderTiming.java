// Ported from RatOptimization's oculus_shaders module (XenonGlProgramBuilderMixin; MIT, Copyright (c) 2026
// SolarBiscuit) into the Visus compile service. Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package net.irisshaders.iris.compat.sodium.mixin.compile;

import dev.visus.compile.ShaderBuild;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * Visus compile service: the link site of Embeddium/Xenon programs (Visus's terrain shaders go through it), timed for
 * the build summary line. Terrain links are not warmed up (see {@link ShaderBuild}). Outside a build this only checks
 * a field.
 */
@Mixin(value = GlProgram.Builder.class, remap = false)
public abstract class MixinGlProgramBuilderTiming {
	@Shadow
	@Final
	private int program;
	@Shadow
	@Final
	private ResourceLocation name;

	@Inject(method = "link", at = @At("HEAD"))
	private void visus$linkStart(Function<?, ?> factory, CallbackInfoReturnable<?> cir) {
		ShaderBuild.linkStart();
	}

	@Inject(method = "link", at = @At("RETURN"))
	private void visus$linkEnd(Function<?, ?> factory, CallbackInfoReturnable<?> cir) {
		ShaderBuild.linkEnd(String.valueOf(this.name), this.program);
	}
}
