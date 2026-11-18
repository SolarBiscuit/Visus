// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** One pack sampler. */
public interface PackSampler {
	String name();

	/** The GL texture to bind this frame. It can change, for example when targets flip or resize. */
	int texture();

	/** The GL sampler object to bind with it, or 0 for none. */
	int samplerObject();
}
