// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

import java.util.Collection;

/**
 * Declares extra source files a shader pack may ship for a LOD renderer (for example {@code voxy.json} and
 * {@code voxy_opaque.glsl}), and reads them when a pack's program set is built.
 */
public interface LodProgramProvider {
	/** A short, stable id for logs, for example {@code "voxy"}. */
	String id();

	/**
	 * File names, relative to the pack's {@code shaders/} folder or a dimension folder, that the pack may contain.
	 * Visus also looks for each of them under {@code program/}. Asked once, before the first pack loads.
	 */
	Collection<String> extraSourceNames();

	/**
	 * How many colour targets beyond {@code colortex15} this provider may need (0 to 184). Visus then allows
	 * {@code colortex0} up to {@code colortex(15 + the largest value over all providers)}. Asked once, before the
	 * first pack loads.
	 */
	default int extraColorTargets() {
		return 0;
	}

	/**
	 * Called once per program set (one per dimension) while a pack loads, on the thread that loads it, before the
	 * pipeline is created. Read your files here, and declare the pack uniforms you will need through
	 * {@link PackSources#retainUniforms}.
	 *
	 * @throws LodPackException if the pack's files for this provider are invalid; Visus logs the message, disables this
	 *                          provider for this program set and keeps loading the pack
	 */
	void onProgramSetBuilt(PackSources sources) throws LodPackException;
}
