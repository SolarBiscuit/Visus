// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/**
 * Adds defines and uniforms to every pack program, for example {@code VOXY} and the LOD projection matrices. Asked on
 * every pack load. Both methods have empty defaults.
 */
public interface UniformContributor {
	/** Adds preprocessor defines to every pack program. */
	default void defines(DefineSink sink) {
	}

	/** Adds uniforms and samplers to every pack program. */
	default void uniforms(UniformSink sink) {
	}
}
