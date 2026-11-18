// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

import java.util.List;
import java.util.Set;

/** Pack samplers found by {@link PipelineView#packSamplers}. */
public interface PackSamplers {
	/** The samplers that were found, in a stable order; {@link #bind} puts sampler i on unit {@code baseUnit + i}. */
	List<PackSampler> found();

	/** The requested names that the pack does not provide. */
	Set<String> missing();

	/**
	 * Binds every found sampler's current texture and sampler object to {@code baseUnit + i}, through Visus's state
	 * tracking. Use a base from {@link SamplerUnits#allocateTextureUnits}. Does not allocate.
	 */
	void bind(int baseUnit);
}
