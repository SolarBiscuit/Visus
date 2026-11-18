// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.spi;

import dev.visus.api.VisusApi;

import java.util.Objects;

/**
 * Holds the installed {@link VisusApi}. <b>For Visus itself only:</b> Visus calls {@link #install} once, while it is
 * constructed. Mods use {@link VisusApi#get()}.
 */
public final class ApiInstance {
	private static volatile VisusApi instance;

	private ApiInstance() {
	}

	/** The installed implementation, or null. */
	public static VisusApi get() {
		return instance;
	}

	/**
	 * Installs the implementation.
	 *
	 * @throws IllegalStateException if one is already installed
	 */
	public static synchronized void install(VisusApi api) {
		Objects.requireNonNull(api, "api");
		if (instance != null) {
			throw new IllegalStateException("A VisusApi implementation is already installed: " + instance.getClass().getName());
		}
		instance = api;
	}
}
