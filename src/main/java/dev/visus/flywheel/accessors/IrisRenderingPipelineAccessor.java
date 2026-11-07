// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.accessors;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.minecraft.client.renderer.ShaderInstance;

import java.io.IOException;

public interface IrisRenderingPipelineAccessor {
    ProgramSet getProgramSet();

    ShaderInstance callCreateShader(String name, ProgramSource source, ProgramId programId, AlphaTest fallbackAlpha,
                                    VertexFormat vertexFormat, FogMode fogMode,
                                    boolean isIntensity, boolean isFullbright, boolean isGlint, boolean isText) throws IOException;


    ShaderInstance callCreateShadowShader(String name, ProgramSource source, ProgramId programId, AlphaTest fallbackAlpha,
                                          VertexFormat vertexFormat, boolean isIntensity, boolean isFullbright, boolean isText) throws IOException;

    //IrisShaderProgram getFlwShaderProgram(ProgramContext context);
}
