// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.gl;

/** GL state helpers; see {@link dev.visus.api.VisusApi#gl()}. */
public interface GlApi {
	/**
	 * Opens a scope for raw GL work. On {@link GlScope#close()} the GL state Minecraft and Visus keep track of is put
	 * back as they believe it to be, whatever raw GL ran inside: blend, depth, colour mask, cull, the active texture and
	 * the textures and sampler objects of the tracked units, the program, the viewport, the framebuffers bound when the
	 * scope opened, and the shader pack's storage buffer bindings. Per-buffer blend state changed through the scope's
	 * methods is restored exactly. The texture
	 * units and storage bindings {@code owner} got from {@link dev.visus.api.lod.SamplerUnits} are its own and are left
	 * as they are. Scopes do not nest. Render thread only; does not allocate.
	 *
	 * @throws IllegalStateException if a scope is already open
	 */
	GlScope openScope(String owner);
}
