package dev.visus.mixin.shadow;

import dev.visus.config.VisusConfig;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shadow skip list (architecture §4.4, from CoO): no sign text in the shadow map. */
@Mixin(SignRenderer.class)
public abstract class ShadowSkipSignTextMixin {
	@Inject(method = "renderSignText", at = @At("HEAD"), cancellable = true)
	private void visus$skipInShadow(CallbackInfo ci) {
		if (VisusConfig.get().skipSignTextInShadow && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ci.cancel();
		}
	}
}
