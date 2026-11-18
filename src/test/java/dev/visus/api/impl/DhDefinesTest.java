package dev.visus.api.impl;

import dev.visus.api.lod.DefineSink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The DH defines now come through the API's contributor path. They must be exactly what Oculus 1.8.0's
 * {@code StandardMacros.createStandardEnvironmentDefines} wrote, in the same order, or pack output changes (T1).
 */
class DhDefinesTest {
	/** Oculus 1.8.0, StandardMacros lines 54-73, as key=value ("" for a flag). */
	private static final List<String> OCULUS_1_8_0 = List.of(
		"DH_BLOCK_UNKNOWN=0", "DH_BLOCK_LEAVES=1", "DH_BLOCK_STONE=2", "DH_BLOCK_WOOD=3", "DH_BLOCK_METAL=4",
		"DH_BLOCK_DIRT=5", "DH_BLOCK_LAVA=6", "DH_BLOCK_DEEPSLATE=7", "DH_BLOCK_SNOW=8", "DH_BLOCK_SAND=9",
		"DH_BLOCK_TERRACOTTA=10", "DH_BLOCK_NETHER_STONE=11", "DH_BLOCK_WATER=12", "DH_BLOCK_GRASS=13",
		"DH_BLOCK_AIR=14", "DH_BLOCK_ILLUMINATED=15");

	private static List<String> emit(boolean dhRendering) {
		List<String> out = new ArrayList<>();
		new DhDefines(() -> dhRendering).defines(new DefineSink() {
			@Override
			public void define(String name) {
				out.add(name + "=");
			}

			@Override
			public void define(String name, String value) {
				out.add(name + "=" + value);
			}
		});
		return out;
	}

	@Test
	void withoutDistantHorizonsTheBlockIdsOnly() {
		assertEquals(OCULUS_1_8_0, emit(false));
	}

	@Test
	void withDistantHorizonsTheFlagComesFirst() {
		List<String> expected = new ArrayList<>();
		expected.add("DISTANT_HORIZONS=");
		expected.addAll(OCULUS_1_8_0);
		assertEquals(expected, emit(true));
	}
}
