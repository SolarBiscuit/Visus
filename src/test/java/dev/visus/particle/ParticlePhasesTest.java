package dev.visus.particle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V-10: each particle render type draws in exactly one phase. */
class ParticlePhasesTest {
	// Stand-ins for the vanilla ParticleRenderType constants.
	static final Object TERRAIN_SHEET = "TERRAIN_SHEET";
	static final Object SHEET_OPAQUE = "PARTICLE_SHEET_OPAQUE";
	static final Object SHEET_TRANSLUCENT = "PARTICLE_SHEET_TRANSLUCENT";
	static final Object SHEET_LIT = "PARTICLE_SHEET_LIT";
	static final Object CUSTOM = "CUSTOM";
	static final Object NO_RENDER = "NO_RENDER";
	static final List<Object> OPAQUE = List.of(SHEET_OPAQUE, SHEET_LIT, CUSTOM, NO_RENDER);
	static final List<Object> VANILLA = List.of(TERRAIN_SHEET, SHEET_OPAQUE, SHEET_TRANSLUCENT, SHEET_LIT, CUSTOM, NO_RENDER);

	static ParticlePhases<Object> phases() {
		return new ParticlePhases<>(OPAQUE, VANILLA);
	}

	static int phasesDrawn(Predicate<Object> opaquePhase, Predicate<Object> translucentPhase, Object type) {
		return (opaquePhase.test(type) ? 1 : 0) + (translucentPhase.test(type) ? 1 : 0);
	}

	@Test
	void oculusFiltersDrewModdedTypesTwice() {
		// Oculus 1.8.0 MixinParticleEngine, MIXED ordering.
		Set<Object> opaqueSet = Set.copyOf(OPAQUE);
		Predicate<Object> oculusOpaque = type -> !type.equals(SHEET_TRANSLUCENT);
		Predicate<Object> oculusTranslucent = type -> !opaqueSet.contains(type);
		Object modded = new Object();

		assertEquals(2, phasesDrawn(oculusOpaque, oculusTranslucent, modded));
		assertEquals(2, phasesDrawn(oculusOpaque, oculusTranslucent, TERRAIN_SHEET));
	}

	@Test
	void everyTypeDrawsInExactlyOnePhase() {
		ParticlePhases<Object> p = phases();
		Object modded = new Object();

		for (Object type : List.of(TERRAIN_SHEET, SHEET_OPAQUE, SHEET_TRANSLUCENT, SHEET_LIT, CUSTOM, NO_RENDER, modded)) {
			assertEquals(1, phasesDrawn(p::drawsInOpaquePhase, p::drawsInTranslucentPhase, type), String.valueOf(type));
		}

		assertTrue(p.drawsInOpaquePhase(SHEET_OPAQUE));
		assertTrue(p.drawsInTranslucentPhase(SHEET_TRANSLUCENT));
		assertTrue(p.drawsInTranslucentPhase(TERRAIN_SHEET), "vanilla draws it after translucent terrain");
	}

	@Test
	void unknownTypesStartTranslucentAndAreLearnedOnce() {
		ParticlePhases<Object> p = phases();
		Object solid = new Object();
		Object glowy = new Object();

		assertTrue(p.needsProbe(solid));
		assertTrue(p.drawsInTranslucentPhase(solid), "vanilla timing until classified");
		assertFalse(p.needsProbe(SHEET_TRANSLUCENT), "vanilla types are never probed");

		assertEquals(ParticlePhases.Kind.OPAQUE, p.learn(solid, false, true));
		assertEquals(ParticlePhases.Kind.TRANSLUCENT, p.learn(glowy, true, false));
		assertFalse(p.needsProbe(solid));
		assertTrue(p.drawsInOpaquePhase(solid));
		assertFalse(p.drawsInTranslucentPhase(solid));
		assertTrue(p.drawsInTranslucentPhase(glowy));
		assertEquals(1, phasesDrawn(p::drawsInOpaquePhase, p::drawsInTranslucentPhase, solid));
	}

	@Test
	void blendedOrDepthMaskedTypesStayTranslucent() {
		ParticlePhases<Object> p = phases();

		assertEquals(ParticlePhases.Kind.TRANSLUCENT, p.learn(new Object(), true, true));
		assertEquals(ParticlePhases.Kind.TRANSLUCENT, p.learn(new Object(), false, false));
	}

	@Test
	void clearForgetsLearnedTypes() {
		ParticlePhases<Object> p = phases();
		Object solid = new Object();
		p.learn(solid, false, true);
		p.clearLearned();

		assertTrue(p.needsProbe(solid));
		assertTrue(p.drawsInTranslucentPhase(solid));
	}
}
