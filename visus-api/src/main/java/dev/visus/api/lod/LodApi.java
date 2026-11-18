// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/**
 * Hooks for level-of-detail renderers (Distant Horizons style mods) that draw with the shader pack's programs.
 * Register during mod construction or client setup, before the first shader pack loads.
 */
public interface LodApi {
	/** Adds a provider of extra pack programs. Providers are asked on every pack load, in registration order. */
	void register(LodProgramProvider provider);

	/** Adds a listener for pipeline creation, level rendering and destruction. */
	void addListener(LodPipelineListener listener);

	/** Adds defines, uniforms and samplers to every pack program. */
	void register(UniformContributor contributor);

	/** The central allocator for texture units and buffer bindings outside the pack's own. */
	SamplerUnits samplerUnits();
}
