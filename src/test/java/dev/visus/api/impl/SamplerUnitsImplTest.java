package dev.visus.api.impl;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NeoVoxy #10: its LOD pass bound the pack's samplers from a hard-coded unit 6, which a pack program could also be
 * using. Units from the allocator come from the top down and are reserved, so pack programs never get them.
 */
class SamplerUnitsImplTest {
	@Test
	void unitsComeFromTheTopAndAreReserved() {
		SamplerUnitsImpl units = new SamplerUnitsImpl();
		int voxy = units.allocateTextureUnits("voxy", 6);
		assertEquals(26, voxy);
		assertEquals(Set.of(26, 27, 28, 29, 30, 31), units.reservedTextureUnits());
		int other = units.allocateTextureUnits("other", 2);
		assertEquals(24, other, "the next owner gets the units below");
		assertTrue(units.reservedTextureUnits().containsAll(Set.of(24, 25)));
	}

	@Test
	void hardCodedUnitSixIsNeverHandedOut() {
		SamplerUnitsImpl units = new SamplerUnitsImpl();
		units.allocateTextureUnits("voxy", 16);
		assertFalse(units.reservedTextureUnits().contains(6), "packs take units from 0 up; a pack's first samplers stay free of API units");
		assertFalse(units.reservedTextureUnits().contains(0));
		assertFalse(units.reservedTextureUnits().contains(2), "vanilla's lightmap unit");
	}

	@Test
	void askingAgainReturnsTheSameRange() {
		SamplerUnitsImpl units = new SamplerUnitsImpl();
		int first = units.allocateTextureUnits("voxy", 4);
		int version = units.version();
		assertEquals(first, units.allocateTextureUnits("voxy", 4), "stable across pack reloads");
		assertEquals(version, units.version(), "no new allocation, so no reload is needed");
		assertArrayEquals(new int[]{first, 4}, units.textureUnitsOf("voxy"));
	}

	@Test
	void askingAgainWithAnotherCountFails() {
		SamplerUnitsImpl units = new SamplerUnitsImpl();
		units.allocateTextureUnits("voxy", 4);
		assertThrows(IllegalStateException.class, () -> units.allocateTextureUnits("voxy", 5));
	}

	@Test
	void exhaustionIsReportedClearly() {
		SamplerUnitsImpl units = new SamplerUnitsImpl();
		units.allocateTextureUnits("big", SamplerUnitsImpl.TEXTURE_TOP - SamplerUnitsImpl.TEXTURE_FLOOR);
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> units.allocateTextureUnits("late", 1));
		assertTrue(e.getMessage().contains("late"), e.getMessage());
	}

	@Test
	void storageBindingsStayAboveThePackRange() {
		SamplerUnitsImpl units = new SamplerUnitsImpl();
		int first = units.allocateStorageBindings("voxy", 8);
		assertEquals(24, first);
		assertTrue(first >= SamplerUnitsImpl.STORAGE_FLOOR);
		assertThrows(IllegalStateException.class, () -> units.allocateStorageBindings("more", 9),
			"bindings 16..23 are left, not 9");
	}

	@Test
	void badArgumentsAreRejected() {
		SamplerUnitsImpl units = new SamplerUnitsImpl();
		assertThrows(IllegalArgumentException.class, () -> units.allocateTextureUnits("", 1));
		assertThrows(IllegalArgumentException.class, () -> units.allocateTextureUnits("x", 0));
	}
}
