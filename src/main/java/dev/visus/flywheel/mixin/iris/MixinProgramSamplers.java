// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.mixin.iris;

import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.gl.GlTextureUnit;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Set;
import java.util.HashSet;

@Mixin(value = ProgramSamplers.class,remap = false)
public class MixinProgramSamplers {
    @ModifyVariable(method = "builder", at = @At("LOAD"), argsOnly = true)
    private static Set<Integer> modifyReservedTextureUnits(Set<Integer> var1) {
        // Add the lighting volume texture from Create to the reserved textures.
        //
        // The set from Iris is immutable, so duplicate before modifying.
        Set<Integer> reservedTextureUnits = new HashSet<Integer>();
        reservedTextureUnits.addAll(var1);
//        reservedTextureUnits.add(4);
        reservedTextureUnits.add(Samplers.LIGHT_LUT.number);
        reservedTextureUnits.add(Samplers.LIGHT_SECTIONS.number);
        return reservedTextureUnits;
    }
}
