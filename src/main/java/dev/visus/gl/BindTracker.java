package dev.visus.gl;

import org.lwjgl.opengl.GL30C;

/**
 * The single record of framebuffer and program bindings made through {@code GlStateManager} (architecture L0, bug
 * ledger V-14).
 *
 * <p>Oculus used these values to skip binds it thought were redundant. The framebuffer check compared the cached
 * framebuffer against the bind <em>target</em> enum, so it almost never skipped. The program check did skip, and it
 * went stale whenever a mod bound a program through raw GL, which Xenon/Embeddium does in {@code GlProgram.bind()}.
 * A stale skip leaves the wrong program or framebuffer bound. Visus never skips. It only records what was
 * requested, so nothing can go out of sync with the real GL state in a way that changes rendering.
 *
 * <p>Render thread only.
 */
public final class BindTracker {
	private static int drawFramebuffer;
	private static int readFramebuffer;
	private static int program;

	private BindTracker() {
	}

	public static void onBindFramebuffer(int target, int framebuffer) {
		switch (target) {
			case GL30C.GL_FRAMEBUFFER -> {
				drawFramebuffer = framebuffer;
				readFramebuffer = framebuffer;
			}
			case GL30C.GL_DRAW_FRAMEBUFFER -> drawFramebuffer = framebuffer;
			case GL30C.GL_READ_FRAMEBUFFER -> readFramebuffer = framebuffer;
			default -> throw new IllegalStateException("Invalid framebuffer target: " + target);
		}
	}

	public static void onDeleteFramebuffer(int framebuffer) {
		if (drawFramebuffer == framebuffer) {
			drawFramebuffer = 0;
		}

		if (readFramebuffer == framebuffer) {
			readFramebuffer = 0;
		}
	}

	public static void onUseProgram(int id) {
		program = id;
	}

	/** The draw framebuffer last bound through {@code GlStateManager}. Raw GL binds are not seen. */
	public static int lastDrawFramebuffer() {
		return drawFramebuffer;
	}

	/** The read framebuffer last bound through {@code GlStateManager}. Raw GL binds are not seen. */
	public static int lastReadFramebuffer() {
		return readFramebuffer;
	}

	/** The program last bound through {@code GlStateManager}. Raw GL binds are not seen. */
	public static int lastProgram() {
		return program;
	}

	/** For tests. */
	public static void reset() {
		drawFramebuffer = 0;
		readFramebuffer = 0;
		program = 0;
	}
}
