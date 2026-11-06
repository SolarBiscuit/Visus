package dev.visus.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL30C;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V-06. */
class DepthFormatsTest {
	@Test
	void bitsMapToSizedFormats() {
		assertEquals(GL30C.GL_DEPTH_COMPONENT32F, DepthFormats.fromBits(32, GL30C.GL_FLOAT, 0));
		assertEquals(GL30C.GL_DEPTH_COMPONENT32, DepthFormats.fromBits(32, DepthFormats.UNSIGNED_NORMALIZED, 0));
		assertEquals(GL30C.GL_DEPTH_COMPONENT24, DepthFormats.fromBits(24, DepthFormats.UNSIGNED_NORMALIZED, 0));
		assertEquals(GL30C.GL_DEPTH_COMPONENT16, DepthFormats.fromBits(16, DepthFormats.UNSIGNED_NORMALIZED, 0));
		assertEquals(GL30C.GL_DEPTH24_STENCIL8, DepthFormats.fromBits(24, DepthFormats.UNSIGNED_NORMALIZED, 8));
		assertEquals(GL30C.GL_DEPTH32F_STENCIL8, DepthFormats.fromBits(32, GL30C.GL_FLOAT, 8));
		assertEquals(-1, DepthFormats.fromBits(0, 0, 0));
		assertEquals(-1, DepthFormats.fromBits(24, GL30C.GL_FLOAT, 0));
	}

	@Test
	void unsizedRequestsAreNotSized() {
		assertFalse(DepthFormats.isSized(GL30C.GL_DEPTH_COMPONENT));
		assertFalse(DepthFormats.isSized(GL30C.GL_DEPTH_STENCIL));
		assertTrue(DepthFormats.isSized(GL30C.GL_DEPTH24_STENCIL8));
		assertTrue(DepthFormats.isSized(GL30C.GL_DEPTH_COMPONENT32F));
	}
}
