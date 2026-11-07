package dev.visus.mixin.shadow;

import dev.visus.config.VisusConfig;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shadow skip list (architecture §4.4, from CoO): no entity name tags in the shadow map. */
@Mixin(EntityRenderer.class)
public abstract class ShadowSkipNameTagMixin {
	@Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
	private void visus$skipInShadow(CallbackInfo ci) {
		if (VisusConfig.get().skipNameTagsInShadow && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ci.cancel();
		}
	}
}
