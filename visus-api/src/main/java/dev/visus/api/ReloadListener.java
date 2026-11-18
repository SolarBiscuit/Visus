// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api;

/** Runs after a shader pipeline reload; see {@link VisusApi#addReloadListener}. */
@FunctionalInterface
public interface ReloadListener {
	/** @param packInUse whether a shader pack is in use after the reload */
	void onPackReloaded(boolean packInUse);
}
