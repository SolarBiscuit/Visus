// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

import java.util.Collection;
import java.util.Optional;

/** The source files of the program set being built; see {@link LodProgramProvider#onProgramSetBuilt}. */
public interface PackSources {
	/** The shader pack's name. */
	String packName();

	/**
	 * The folder this program set reads from, relative to the pack's {@code shaders/} folder: empty for the pack root,
	 * or a dimension folder such as {@code world0} or {@code world-1}.
	 */
	String directory();

	/**
	 * Reads a source file of this program set, relative to {@link #directory()} (for example {@code voxy.json} or
	 * {@code program/voxy.json}). The text has gone through the pack's preprocessor, as the pack's own sources have.
	 * Only files the pack loaded can be read: its program sources, the files they include, and the files named by a
	 * provider's {@link LodProgramProvider#extraSourceNames()}. Empty otherwise.
	 */
	Optional<String> read(String relativePath);

	/**
	 * Declares pack uniforms (built-in or custom) that you will read through {@link PipelineView#packUniforms}. Custom
	 * uniforms that no pack program uses are normally dropped when the pipeline is built; declared ones are kept.
	 */
	void retainUniforms(Collection<String> names);
}
