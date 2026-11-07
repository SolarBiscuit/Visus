package dev.visus.mixin.shadow;

import dev.visus.config.VisusConfig;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shadow skip list (architecture §4.4, from CoO): items report no enchantment glint while the shadow map renders, so
 * the glint layer (an extra draw per item) is not drawn there.
 */
@Mixin(ItemStack.class)
public abstract class ShadowSkipGlintMixin {
	@Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
	private void visus$noGlintInShadow(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && VisusConfig.get().skipGlintInShadow && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			cir.setReturnValue(false);
		}
	}
}
