// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** Writes a value into a float array that the caller owns and reuses. */
@FunctionalInterface
public interface FloatArraySource {
	void write(float[] dst);
}
