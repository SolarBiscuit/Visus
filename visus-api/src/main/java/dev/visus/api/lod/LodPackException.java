// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/** Thrown by a {@link LodProgramProvider} when a pack's files for it are invalid. */
public class LodPackException extends Exception {
	public LodPackException(String message) {
		super(message);
	}

	public LodPackException(String message, Throwable cause) {
		super(message, cause);
	}
}
