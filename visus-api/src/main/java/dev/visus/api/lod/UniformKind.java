// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** GLSL types a pack uniform can have. */
public enum UniformKind {
	INT("int", 1, false),
	FLOAT("float", 1, true),
	VEC2("vec2", 2, true),
	VEC2I("ivec2", 2, false),
	VEC3("vec3", 3, true),
	VEC3I("ivec3", 3, false),
	VEC4("vec4", 4, true),
	VEC4I("ivec4", 4, false),
	MAT3("mat3", 9, true),
	MAT4("mat4", 16, true);

	private final String glsl;
	private final int components;
	private final boolean floating;

	UniformKind(String glsl, int components, boolean floating) {
		this.glsl = glsl;
		this.components = components;
		this.floating = floating;
	}

	/** The GLSL type name. */
	public String glsl() {
		return glsl;
	}

	/** The number of 4-byte components. */
	public int components() {
		return components;
	}

	/** The size of the tightly packed value in bytes. */
	public int bytes() {
		return components * 4;
	}

	/** Whether the components are floats (otherwise ints). */
	public boolean floating() {
		return floating;
	}
}
