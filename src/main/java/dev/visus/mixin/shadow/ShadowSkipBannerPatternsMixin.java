package dev.visus.mixin.shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import dev.visus.config.VisusConfig;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Shadow skip list (architecture §4.4, from CoO): in the shadow map a banner draws only its base cloth, which is the
 * same shape as the patterned banner, and none of its pattern layers (one draw each).
 */
@Mixin(BannerRenderer.class)
public abstract class ShadowSkipBannerPatternsMixin {
	@Inject(method = "renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLjava/util/List;Z)V",
		at = @At("HEAD"), cancellable = true)
	private static void visus$baseOnlyInShadow(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, ModelPart flag,
											   Material material, boolean banner, List<Pair<Holder<BannerPattern>, DyeColor>> patterns,
											   boolean glint, CallbackInfo ci) {
		if (VisusConfig.get().skipBannerPatternsInShadow && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			// The first thing vanilla draws: the base cloth.
			flag.render(poseStack, material.buffer(buffer, RenderType::entitySolid, glint), light, overlay);
			ci.cancel();
		}
	}
}
