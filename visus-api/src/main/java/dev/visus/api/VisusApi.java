// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api;

import dev.visus.api.gl.GlApi;
import dev.visus.api.lod.LodApi;
import dev.visus.api.spi.ApiInstance;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Entry point of the Visus API.
 *
 * <p>Mods compile against {@code dev.visus:visus-api} and call {@link #get()} at run time. It is empty when Visus is
 * not installed (for example with plain Oculus), so a mod can keep a separate code path for that case. Do not load
 * classes that reference this API unless the {@code visus} mod is loaded.
 *
 * <p>Threading: every method is for the render thread unless its documentation says otherwise. Registration methods
 * ({@link LodApi#register}, {@link #addReloadListener}) may be called during mod construction or client setup.
 */
public interface VisusApi {
	/**
	 * The revision this copy of the API was compiled as. Raised for every addition, so a mod can check
	 * {@code VisusApi.get().map(VisusApi::revision).orElse(0) >= N} before using something added in revision N.
	 */
	int REVISION = 1;

	/** The installed implementation, or empty when Visus is not loaded. Safe to call from any thread. */
	static Optional<VisusApi> get() {
		return Optional.ofNullable(ApiInstance.get());
	}

	/** The revision the installed Visus implements; may be higher than {@link #REVISION} for a newer Visus. */
	int revision();

	/** Whether a shader pack is loaded and rendering. Cheap; may be polled every frame. */
	boolean isShaderPackInUse();

	/** Whether the shadow pass is being rendered right now. */
	boolean isRenderingShadowPass();

	/** The file or folder name of the current shader pack, or empty when shaders are off. */
	Optional<String> currentPackName();

	/** The {@code shaderpacks} folder. Safe to call from any thread. */
	Path shaderpacksDirectory();

	/**
	 * Asks Visus to rebuild the shader pipeline. Never runs immediately: the reload happens once, at the next frame
	 * boundary, however often it is requested and from wherever (including from inside a pipeline callback).
	 *
	 * @param reason a short text for the log
	 */
	void requestReload(String reason);

	/**
	 * Adds a listener that runs after every pipeline reload. Order: the API state ({@link #isShaderPackInUse()},
	 * {@link #currentPackName()}) is updated first, then the world renderer is rebuilt if the reload requires it, then
	 * the listeners run.
	 */
	void addReloadListener(ReloadListener listener);

	/** Programs, uniforms and pipeline events for level-of-detail renderers. */
	LodApi lod();

	/** GL state helpers that keep Visus's state tracking correct. */
	GlApi gl();
}
