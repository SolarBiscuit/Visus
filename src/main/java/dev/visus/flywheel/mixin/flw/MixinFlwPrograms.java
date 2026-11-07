// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.flw;

import dev.engine_room.flywheel.backend.compile.FlwPrograms;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.engine_room.flywheel.backend.glsl.SourceComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.visus.flywheel.backend.IrisInstancingPrograms;

import java.util.List;

@Mixin(FlwPrograms.class)
public class MixinFlwPrograms {

    @Final
    @Shadow
    private static ResourceLocation COMPONENTS_HEADER_FRAG;

    @Shadow
    public static ShaderSources SOURCES;


    @Inject(method = "reload", at = @At("TAIL"), remap = false)
    private static void irisflw$reload$tail(ResourceManager resourceManager, CallbackInfo ci) {
        IrisInstancingPrograms.setInstance(null);

        var fragmentComponentsHeader = SOURCES.get(COMPONENTS_HEADER_FRAG);

        List<SourceComponent> vertexComponents = List.of();
        List<SourceComponent> fragmentComponents = List.of(fragmentComponentsHeader);

        IrisInstancingPrograms.reload(SOURCES, vertexComponents, fragmentComponents);
    }
}
