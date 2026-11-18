// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/**
 * Pipeline events for a LOD renderer. All callbacks run on the render thread.
 *
 * <p>Visus keeps one pipeline per dimension, so several pipelines can exist at once (for example after visiting the
 * Nether and coming back). Each callback names the pipeline it is about; the views are distinct objects, so they can
 * be used as map keys.
 */
public interface LodPipelineListener {
	/**
	 * A shader pack pipeline is ready. Called after the pipeline is fully built and installed, never from inside its
	 * construction, so {@link dev.visus.api.VisusApi#requestReload} is safe here. The view stays valid until
	 * {@link #onPipelineDestroyed} is called with it.
	 */
	void onPipelineCreated(PipelineView pipeline);

	/** {@code pipeline} starts rendering a level (once per frame, before any pack pass). */
	default void onBeginLevel(PipelineView pipeline, FrameContext frame) {
	}

	/** {@code pipeline} is being destroyed. Release what you built from it. */
	void onPipelineDestroyed(PipelineView pipeline);
}
