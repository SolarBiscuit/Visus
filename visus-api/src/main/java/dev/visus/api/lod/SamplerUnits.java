// SPDX-License-Identifier: MIT
// Copyright (c) 2026 SolarBiscuit. Part of visus-api; see visus-api/LICENSE.
package dev.visus.api.lod;

/**
 * Central allocator for texture units and shader storage buffer bindings used outside the pack's own programs. Units
 * handed out here are never given to a pack program's samplers, so binding them cannot overwrite the pack's textures.
 *
 * <p><b>Reserve texture units sparingly.</b> Every reserved unit is taken away from every pack program, and packs with
 * many samplers need most of the 32. Only reserve units that must stay bound outside your own rendering. For work done
 * inside a {@link dev.visus.api.gl.GlScope} you need none: bind any unit there, because pack programs bind their
 * samplers again whenever they are used and the scope restores the units Minecraft tracks.
 *
 * <p>Allocate during client setup, before the first pack loads. Asking again with the same owner and count returns the
 * same range, so allocations survive pack reloads.
 */
public interface SamplerUnits {
	/**
	 * Reserves {@code count} consecutive texture units.
	 *
	 * @return the first unit
	 * If a pack is already loaded, Visus reloads it at the next frame boundary, so that its programs leave the new units
	 * free.
	 *
	 * @throws IllegalStateException if not enough units are left, or the owner asks again with a different count
	 */
	int allocateTextureUnits(String owner, int count);

	/**
	 * Reserves {@code count} consecutive shader storage buffer bindings, above every binding a pack can use.
	 *
	 * @return the first binding
	 * @throws IllegalStateException if not enough bindings are left, or the owner asks again with a different count
	 */
	int allocateStorageBindings(String owner, int count);
}
