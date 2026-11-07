package dev.visus.compat.geckolib.mixin;

import dev.visus.compat.geckolib.RenderPassTracker;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * V-09: GeckoLib animations posed in both the shadow and the main pass (see {@link RenderPassTracker}).
 *
 * <p>Applied only to GeckoLib 4.x ({@code GeckoLibCompatMixinPlugin}). No GeckoLib types are referenced, so Visus needs
 * no GeckoLib dependency. An injection, not an overwrite: GeckoLib's own timing is kept.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.model.GeoModel", remap = false)
public abstract class GeoModelMixin {
	@Shadow
	private long lastRenderedInstance;

	@Unique
	private final RenderPassTracker visus$passes = new RenderPassTracker();

	@Inject(method = "handleAnimations", at = @At("HEAD"))
	private void visus$poseInEveryPass(CallbackInfo ci) {
		if (visus$passes.passChanged(ShadowRenderingState.areShadowsCurrentlyBeingRendered())) {
			lastRenderedInstance = -1L;
		}
	}
}
