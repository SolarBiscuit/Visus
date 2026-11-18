package dev.visus.api.impl;

import dev.visus.api.lod.DefineSink;
import dev.visus.api.lod.UniformContributor;

import java.util.function.BooleanSupplier;

/**
 * Distant Horizons' pack defines, contributed through the same {@link UniformContributor} path as any API user
 * (architecture §4.5: the DH compatibility sits behind the LOD API). The defines and their order are exactly those
 * Oculus 1.8.0 writes in {@code StandardMacros.createStandardEnvironmentDefines}; it is registered first, so it emits
 * them at the same position. The DH programs, samplers and lifecycle stay in {@code iris.compat.dh}.
 */
public final class DhDefines implements UniformContributor {
	private static final String[] BLOCK_NAMES = {
		"UNKNOWN", "LEAVES", "STONE", "WOOD", "METAL", "DIRT", "LAVA", "DEEPSLATE", "SNOW", "SAND", "TERRACOTTA",
		"NETHER_STONE", "WATER", "GRASS", "AIR", "ILLUMINATED"
	};

	private final BooleanSupplier distantHorizonsRendering;

	/** @param distantHorizonsRendering whether DH is installed and rendering (defines {@code DISTANT_HORIZONS}) */
	public DhDefines(BooleanSupplier distantHorizonsRendering) {
		this.distantHorizonsRendering = distantHorizonsRendering;
	}

	@Override
	public void defines(DefineSink sink) {
		if (distantHorizonsRendering.getAsBoolean()) {
			sink.define("DISTANT_HORIZONS");
		}
		for (int i = 0; i < BLOCK_NAMES.length; i++) {
			sink.define("DH_BLOCK_" + BLOCK_NAMES[i], String.valueOf(i));
		}
	}
}
