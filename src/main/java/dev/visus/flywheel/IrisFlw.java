// Adapted from Iris Flywheel Compat (irisflw) 2.0.5, https://github.com/leon-o/iris-flw-compat (branch 1.20.1-2.0,
// commit 6894e01a3). Copyright (c) 2022 Red Face. MIT License: META-INF/visus/irisflw-LICENSE.
// Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.flywheel;

import net.irisshaders.iris.api.v0.IrisApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.visus.flywheel.backend.IrisFlwBackends;

public final class IrisFlw {
    public static final String MOD_ID = "visus";
    public static final Logger LOGGER = LoggerFactory.getLogger("Visus/Flywheel");
    public static void init() {
        IrisFlwBackends.init();
    }

    public static boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isUsingExtendedVertexFormat() {
        return isShaderPackInUse();
    }
}
