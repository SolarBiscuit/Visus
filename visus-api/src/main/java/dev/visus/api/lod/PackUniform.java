// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** One pack uniform. Its value is current for the frame being rendered. */
public interface PackUniform {
	String name();

	UniformKind kind();

	/**
	 * Writes the current value, tightly packed (no std140 padding) and in native byte order, to a native memory
	 * address such as a mapped buffer. Writes {@link UniformKind#bytes()} bytes and does not allocate. Matrices are
	 * column-major; a {@code mat3} is 9 floats without padding, so a std140 block (which pads each column to 16
	 * bytes) must re-pack it.
	 */
	void write(long address);
}
