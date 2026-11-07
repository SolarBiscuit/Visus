// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.iris;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.shaderpack.include.ShaderPackSourceNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ShaderPackSourceNames.class)
public abstract class MixinShaderPackSourceNames {
    @Invoker(remap = false)
    private static void callAddStarts(ImmutableList.Builder<String> potentialFileNames, String baseName){

    }

    @Inject(method = "findPotentialStarts",remap = false,at = @At(value = "INVOKE",target = "com/google/common/collect/ImmutableList$Builder.build ()Lcom/google/common/collect/ImmutableList;"),
    locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private static void injectFindPotentialStarts(CallbackInfoReturnable<ImmutableList<String>> cir,ImmutableList.Builder<String> builder){
        callAddStarts(builder,"gbuffers_flw");
        callAddStarts(builder,"shadow_flw");
    }
}
