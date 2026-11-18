// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** The frame a level render belongs to; see {@link LodPipelineListener#onBeginLevel}. Valid only during the call. */
public interface FrameContext {
	/** Writes the viewport the level renders into as x, y, width, height into {@code dst[0..3]}. */
	void viewport(int[] dst);

	/** The partial tick of this frame. */
	float partialTick();
}
