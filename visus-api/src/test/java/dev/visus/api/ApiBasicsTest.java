// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api;

import dev.visus.api.lod.RenderTargetInfo;
import dev.visus.api.lod.UniformKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiBasicsTest {
	@Test
	void emptyWithoutVisus() {
		// No implementation is installed in this module's tests, as with plain Oculus.
		assertFalse(VisusApi.get().isPresent());
	}

	@Test
	void uniformKindSizesMatchGlsl() {
		assertEquals(64, UniformKind.MAT4.bytes());
		assertEquals(36, UniformKind.MAT3.bytes());
		assertEquals(12, UniformKind.VEC3I.bytes());
		assertTrue(UniformKind.VEC4.floating());
		assertFalse(UniformKind.VEC2I.floating());
		assertEquals("ivec3", UniformKind.VEC3I.glsl());
	}

	@Test
	void drawTextureFollowsFlip() {
		assertEquals(11, new RenderTargetInfo(3, 10, 11, 0, 1, 1, true).drawTexture());
		assertEquals(10, new RenderTargetInfo(3, 10, 11, 0, 1, 1, false).drawTexture());
	}
}
