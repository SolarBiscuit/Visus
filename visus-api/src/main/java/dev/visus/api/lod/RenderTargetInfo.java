// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/**
 * A pack colour target.
 *
 * @param index          the {@code colortex} index
 * @param mainTexture    the GL texture of the main side
 * @param altTexture     the GL texture of the alternate side
 * @param internalFormat the GL internal format
 * @param width          the width in pixels
 * @param height         the height in pixels
 * @param writesToAlt    whether gbuffer passes write to {@code altTexture} (prepare passes flipped the target)
 */
public record RenderTargetInfo(int index, int mainTexture, int altTexture, int internalFormat, int width, int height,
							   boolean writesToAlt) {
	/** The texture gbuffer passes draw into. */
	public int drawTexture() {
		return writesToAlt ? altTexture : mainTexture;
	}
}
