// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.iris;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.event.RenderContextImpl;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.visus.flywheel.flywheel.RenderLayerEventStateManager;

@Mixin(value = ShadowRenderer.class)
public abstract class MixinShadowRenderer {

    @Final
    @Shadow
    private boolean shouldRenderBlockEntities;

    @Shadow(remap = false)
    @Final
    private float sunPathRotation;

    @Shadow(remap = false)
    @Final
    private float intervalSize;

    @Final
    @Shadow
    private RenderBuffers buffers;

    @Shadow(remap = false)
    public static PoseStack createShadowModelView(float sunPathRotation, float intervalSize)
    {
        return null;
    }

    @Inject(method = "renderShadows",at = @At("HEAD"), remap = false)
    private void injectRenderShadow(LevelRendererAccessor levelRendererAccessor, Camera camera, CallbackInfo ci){
        if (shouldRenderBlockEntities){
            RenderLayerEventStateManager.setRenderingShadow(true);
        }
    }


    @Inject(method = "renderShadows",at = @At("TAIL"), remap = false)
    private void injectRenderShadowTail(LevelRendererAccessor levelRendererAccessor, Camera camera, CallbackInfo ci){
        RenderLayerEventStateManager.setRenderingShadow(false);
    }

    @Inject(method = "renderShadows",
            at = {@At(
            value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
            args = {"ldc=draw entities"},
            remap = true
    )}, remap = false)
    private void injectRenderShadowBeforeDrawEntities(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci){
        if(shouldRenderBlockEntities)
        {
            var poseStack = createShadowModelView(sunPathRotation, intervalSize);
            var flywheel$renderContext = RenderContextImpl.create((LevelRenderer) levelRenderer, levelRenderer.getLevel(),
                    this.buffers, poseStack, ShadowRenderer.PROJECTION, playerCamera, CapturedRenderingState.INSTANCE.getTickDelta());
            VisualizationManager manager = VisualizationManager.get(levelRenderer.getLevel());
            if (manager != null) {
//                manager.renderDispatcher().onStartLevelRender(flywheel$renderContext);
                manager.renderDispatcher().afterEntities(flywheel$renderContext);
            }
        }
    }
}
