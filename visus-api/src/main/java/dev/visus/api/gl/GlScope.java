// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.gl;

/**
 * Raw GL work whose state is restored when the scope closes; see {@link GlApi#openScope}. Use the methods here for
 * per-buffer blend state, which Minecraft's state tracking does not model.
 */
public interface GlScope extends AutoCloseable {
	/** {@code glBlendFuncSeparatei}, restored on close. */
	void blendFuncSeparatei(int buffer, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

	/** {@code glEnablei(GL_BLEND, buffer)}, restored on close. */
	void enableBlendi(int buffer);

	/** {@code glDisablei(GL_BLEND, buffer)}, restored on close. */
	void disableBlendi(int buffer);

	/** Restores the recorded state. Calling it again does nothing. */
	@Override
	void close();
}
