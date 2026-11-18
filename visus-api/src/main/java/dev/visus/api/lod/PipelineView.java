// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

import java.util.Set;

/** Read access to a shader pack pipeline; see {@link LodPipelineListener#onPipelineCreated}. Render thread only. */
public interface PipelineView {
	/** The shader pack's name. */
	String packName();

	/**
	 * The folder of the program set this pipeline was built from, as {@link PackSources#directory()} gave it to
	 * {@link LodProgramProvider#onProgramSetBuilt}: empty for the pack root, or a dimension folder such as
	 * {@code world-1}. Use it to find what you read for this pipeline.
	 */
	String directory();

	/**
	 * The pack's colour target {@code colortex<index>}, created if no pack program uses it yet.
	 *
	 * @throws IllegalArgumentException if the index is outside the allowed range (see
	 *                                  {@link LodProgramProvider#extraColorTargets()})
	 */
	RenderTargetInfo colorTarget(int index);

	/** The pack's {@code separateAo} directive. */
	boolean separateAo();

	/**
	 * The pack's {@code block.properties} id for a block state, or -1 when the pack gives it none. Unlike the other
	 * methods, this one may be called from any thread (for example a model-baking worker) once
	 * {@link LodPipelineListener#onPipelineCreated} has run for this pipeline: the ids do not change while it lives.
	 *
	 * @param blockState a {@code net.minecraft.world.level.block.state.BlockState}
	 */
	int blockStateId(Object blockState);

	/**
	 * Writers for pack uniforms by name. A custom uniform is only available if it was declared through
	 * {@link PackSources#retainUniforms} or a pack program uses it. Build this once per pipeline, not per frame.
	 */
	PackUniforms packUniforms(Set<String> names);

	/** The pack's samplers by name (colortex, depthtex, shadowtex, noisetex, custom textures and so on). */
	PackSamplers packSamplers(Set<String> names);

	/** The GL buffer of the pack's shader storage buffer {@code index}, or 0 when the pack has none there. */
	int storageBuffer(int index);
}
