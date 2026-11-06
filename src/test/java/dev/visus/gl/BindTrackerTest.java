package dev.visus.gl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL30C;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V-14: the tracker records the framebuffer itself (Oculus compared the cache against the target enum). */
class BindTrackerTest {
	@BeforeEach
	void reset() {
		BindTracker.reset();
	}

	@Test
	void framebufferTargetSetsBoth() {
		BindTracker.onBindFramebuffer(GL30C.GL_FRAMEBUFFER, 7);
		assertEquals(7, BindTracker.lastDrawFramebuffer());
		assertEquals(7, BindTracker.lastReadFramebuffer());
	}

	@Test
	void drawAndReadAreSeparate() {
		BindTracker.onBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, 3);
		BindTracker.onBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 4);
		assertEquals(3, BindTracker.lastDrawFramebuffer());
		assertEquals(4, BindTracker.lastReadFramebuffer());
	}

	@Test
	void deleteClearsTheBinding() {
		BindTracker.onBindFramebuffer(GL30C.GL_FRAMEBUFFER, 9);
		BindTracker.onDeleteFramebuffer(9);
		assertEquals(0, BindTracker.lastDrawFramebuffer());
		assertEquals(0, BindTracker.lastReadFramebuffer());
	}

	@Test
	void invalidTargetThrows() {
		assertThrows(IllegalStateException.class, () -> BindTracker.onBindFramebuffer(0x1234, 1));
	}
}
