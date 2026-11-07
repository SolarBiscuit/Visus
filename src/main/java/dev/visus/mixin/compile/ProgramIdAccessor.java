// Ported from RatOptimization's oculus_shaders module (MIT, Copyright (c) 2026 SolarBiscuit) into the Visus
// compile service (architecture §4.4, §5 L2). Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.mixin.compile;

import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GL shader of a warm-up stage whose deletion waits for a link the driver has not finished (getId is protected). */
@Mixin(Program.class)
public interface ProgramIdAccessor {

    @Accessor("id")
    int visus$id();
}
