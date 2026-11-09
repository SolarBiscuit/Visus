package dev.visus.mixin.gl;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.visus.gl.GlStateLocks;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * V-06b: before Forge posts a {@code RenderLevelStageEvent}, release any blend or depth/colour lock a pack program
 * leaked, so the handlers' own blend and mask calls are not dropped. The RenderType overload delegates here.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeRenderStageLockMixin {
	@Inject(method = "dispatchRenderStage(Lnet/minecraftforge/client/event/RenderLevelStageEvent$Stage;"
		+ "Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;I"
		+ "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V", at = @At("HEAD"))
	private static void visus$releaseLeakedLocks(RenderLevelStageEvent.Stage stage, LevelRenderer levelRenderer,
												 PoseStack poseStack, Matrix4f projection, int renderTick,
												 Camera camera, Frustum frustum, CallbackInfo ci) {
		GlStateLocks.releaseLeakedBeforeStage(stage);
	}
}
