// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.backend;

import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.compile.Pipeline;
import dev.engine_room.flywheel.backend.compile.component.BufferTextureInstanceComponent;
import dev.engine_room.flywheel.lib.util.ResourceUtil;

public class IrisFlwPipelines {
    public static final Pipeline IRIS_INSTANCING = Pipeline.builder().compilerMarker("iris_instancing")
            .vertexMain(ResourceUtil.rl("internal/iris_instancing/main.vert"))
            .fragmentMain(ResourceUtil.rl("internal/iris_instancing/main.frag"))
            .assembler(BufferTextureInstanceComponent::new)
            .onLink(program -> {
                program.setSamplerBinding("_flw_instances", Samplers.INSTANCE_BUFFER);
                program.setSamplerBinding("_flw_lightLut", Samplers.LIGHT_LUT);
                program.setSamplerBinding("_flw_lightSections", Samplers.LIGHT_SECTIONS);
            })
            .build();
}
