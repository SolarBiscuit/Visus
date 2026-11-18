// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** How often a contributed uniform is re-read. */
public enum UpdateFrequency {
	/** Once, when the program is built. */
	ONCE,
	/** Once per client tick. */
	PER_TICK,
	/** Once per frame. */
	PER_FRAME
}
