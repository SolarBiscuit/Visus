// Ported from RatOptimization's oculus_shaders module (MIT, Copyright (c) 2026 SolarBiscuit) into the Visus
// compile service (architecture §4.4, §5 L2). Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.mixin.compile;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Uniforms of a warm-up instance own native buffers; they are freed after the batch. */
@Mixin(ShaderInstance.class)
public interface ShaderInstanceUniformsAccessor {

    @Accessor("uniforms")
    List<Uniform> visus$uniforms();
}
