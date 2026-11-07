// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel.accessors;

import net.irisshaders.iris.gl.blending.AlphaTest;

public interface ProgramDirectivesAccessor {
    void setFlwAlphaTestOverride(AlphaTest alphaTest);
}
