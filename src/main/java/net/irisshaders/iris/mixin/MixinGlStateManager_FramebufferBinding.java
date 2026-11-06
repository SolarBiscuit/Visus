package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.visus.gl.BindTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records framebuffer and program binds made through {@link GlStateManager} in {@link BindTracker}.
 *
 * <p>Visus V-14: Oculus used this mixin to cancel binds it believed were redundant. Its framebuffer check compared
 * against the bind target rather than the framebuffer, so it almost never fired. Its program check did fire, and it
 * went stale whenever a mod bound programs through raw GL (Xenon/Embeddium's {@code GlProgram.bind()} does), leaving
 * the wrong program bound. Mods such as Atmospherics carried workarounds for it. Visus no longer cancels any bind.
 */
@Mixin(value = GlStateManager.class, remap = false)
public class MixinGlStateManager_FramebufferBinding {
	@Inject(method = "_glBindFramebuffer(II)V", at = @At("HEAD"))
	private static void iris$trackFramebufferBind(int target, int framebuffer, CallbackInfo ci) {
		BindTracker.onBindFramebuffer(target, framebuffer);
	}

	@Inject(method = "_glUseProgram", at = @At("HEAD"), remap = false)
	private static void iris$trackProgramBind(int program, CallbackInfo ci) {
		BindTracker.onUseProgram(program);
	}

	@Inject(method = "_glDeleteFramebuffers(I)V", at = @At("HEAD"), remap = false)
	private static void iris$trackFramebufferDelete(int framebuffer, CallbackInfo ci) {
		BindTracker.onDeleteFramebuffer(framebuffer);
	}
}
