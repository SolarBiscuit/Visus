// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** Receives preprocessor defines; see {@link UniformContributor#defines}. */
public interface DefineSink {
	/** {@code #define name}. */
	void define(String name);

	/** {@code #define name value}. */
	void define(String name, String value);
}
