// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.backend;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.backend.engine.EngineImpl;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.lib.backend.SimpleBackend;
import dev.engine_room.flywheel.lib.util.ShadersModHelper;
import net.minecraft.resources.ResourceLocation;
import dev.visus.flywheel.IrisFlw;

public class IrisFlwBackends {
    public static final Backend INSTANCING = SimpleBackend.builder()
            .engineFactory(level -> new EngineImpl(level, new IrisInstancedDrawManager(IrisInstancingPrograms.get()), 256))
            .priority(2000)
            .supported(() -> GlCompat.SUPPORTS_INSTANCING && IrisInstancingPrograms.allLoaded() && ShadersModHelper.isShaderPackInUse())
            .register(new ResourceLocation(IrisFlw.MOD_ID, "iris_instancing"));

    public static void init()
    {
        IrisFlw.LOGGER.info("IrisFLW backends initialized: {}", INSTANCING.toString());
    }
}
