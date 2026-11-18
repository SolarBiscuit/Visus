// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Receives uniforms; see {@link UniformContributor#uniforms}. Value sources are called when the uniform is uploaded,
 * at the given frequency, and must not allocate.
 */
public interface UniformSink {
	/** A {@code mat4}. The source writes 16 floats, column-major, into the array it is given. */
	void mat4(String name, UpdateFrequency frequency, FloatArraySource value);

	/** A {@code vec3}. The source writes 3 floats into the array it is given. */
	void vec3(String name, UpdateFrequency frequency, FloatArraySource value);

	/** A {@code float}. */
	void float1(String name, UpdateFrequency frequency, DoubleSupplier value);

	/** An {@code int}. */
	void int1(String name, UpdateFrequency frequency, IntSupplier value);

	/** A sampler bound to the texture the supplier returns when the program is used. */
	void sampler(String name, IntSupplier glTexture);
}
