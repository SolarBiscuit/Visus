// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.accessors;

import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;

import java.util.Optional;
import java.util.function.Function;

public interface ProgramSetAccessor {

    ProgramSource callReadProgramSource(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String program, ProgramSet programSet, ShaderProperties properties, boolean readTessellation);

    ProgramSource callReadProgramSource(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, String program, ProgramSet programSet, ShaderProperties properties, BlendModeOverride var5, boolean readTessellation);

    Optional<ProgramSource> getGbuffersFlw();

    Optional<ProgramSource> getShadowFlw();
}
