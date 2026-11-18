package dev.visus.api.impl;

import dev.visus.api.lod.SamplerUnits;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The central texture-unit and storage-binding allocator (fixes NeoVoxy #10: a hard-coded unit 6 overwrote the pack's
 * first sampler).
 *
 * <p>Pack programs take texture units from 0 upwards, skipping reserved ones ({@code ProgramSamplers.Builder}), so
 * API owners get units from the top down: the first owner gets {@code TEXTURE_TOP - count} and up. 32 is what AMD,
 * NVIDIA and Intel report for {@code GL_MAX_TEXTURE_IMAGE_UNITS}; on a driver with fewer, the units at or above its
 * maximum are dropped from the reserved set (they could not be used by a fragment shader anyway) and reported.
 * Storage bindings work the same way under {@code STORAGE_TOP}, above the low indices packs use for their buffers.
 *
 * <p>No Minecraft or GL types, so it is unit-tested directly.
 */
public final class SamplerUnitsImpl implements SamplerUnits {
	public static final int TEXTURE_TOP = 32;
	public static final int STORAGE_TOP = 32;
	/** Units below this are never handed out: 0-2 are vanilla's texture, overlay and lightmap units. */
	public static final int TEXTURE_FLOOR = 3;
	/** Bindings below this are never handed out, so packs keep their usual low buffer indices. */
	public static final int STORAGE_FLOOR = 16;

	private record Range(int first, int count) {
	}

	private final Map<String, Range> textureUnits = new LinkedHashMap<>();
	private final Map<String, Range> storageBindings = new LinkedHashMap<>();
	private int nextTextureTop = TEXTURE_TOP;
	private int nextStorageTop = STORAGE_TOP;
	private volatile Set<Integer> reservedTextureUnits = Set.of();
	private volatile int version;

	@Override
	public synchronized int allocateTextureUnits(String owner, int count) {
		Range range = allocate(textureUnits, owner, count, nextTextureTop, TEXTURE_FLOOR, "texture units");
		if (range.first < nextTextureTop) {
			nextTextureTop = range.first;
			Set<Integer> reserved = new HashSet<>(reservedTextureUnits);
			for (int i = 0; i < range.count; i++) reserved.add(range.first + i);
			reservedTextureUnits = Set.copyOf(reserved);
			version++;
		}
		return range.first;
	}

	@Override
	public synchronized int allocateStorageBindings(String owner, int count) {
		Range range = allocate(storageBindings, owner, count, nextStorageTop, STORAGE_FLOOR, "storage buffer bindings");
		if (range.first < nextStorageTop) {
			nextStorageTop = range.first;
			version++;
		}
		return range.first;
	}

	private static Range allocate(Map<String, Range> map, String owner, int count, int top, int floor, String what) {
		if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner must not be blank");
		if (count <= 0) throw new IllegalArgumentException("count must be positive: " + count);
		Range existing = map.get(owner);
		if (existing != null) {
			if (existing.count != count) {
				throw new IllegalStateException(owner + " already holds " + existing.count + " " + what + " and asked for " + count);
			}
			return existing;
		}
		int first = top - count;
		if (first < floor) {
			throw new IllegalStateException("Not enough " + what + " left for " + owner + ": asked for " + count + ", "
				+ Math.max(0, top - floor) + " free (allocated: " + map + ")");
		}
		Range range = new Range(first, count);
		map.put(owner, range);
		return range;
	}

	/** Every reserved texture unit; pack programs leave these free. */
	public Set<Integer> reservedTextureUnits() {
		return reservedTextureUnits;
	}

	/** The texture units of {@code owner}, as {first, count}, or null. */
	public synchronized int[] textureUnitsOf(String owner) {
		Range r = textureUnits.get(owner);
		return r == null ? null : new int[]{r.first, r.count};
	}

	/** The storage bindings of {@code owner}, as {first, count}, or null. */
	public synchronized int[] storageBindingsOf(String owner) {
		Range r = storageBindings.get(owner);
		return r == null ? null : new int[]{r.first, r.count};
	}

	/** Raised on every new allocation, so a pack loaded before it can be reloaded. */
	public int version() {
		return version;
	}
}
