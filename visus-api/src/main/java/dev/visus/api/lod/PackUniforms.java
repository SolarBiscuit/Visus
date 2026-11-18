// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

import java.util.List;
import java.util.Set;

/** Pack uniforms found by {@link PipelineView#packUniforms}. */
public interface PackUniforms {
	/** The uniforms that were found, in a stable order. */
	List<PackUniform> found();

	/** The requested names that the pack does not provide. */
	Set<String> missing();
}
