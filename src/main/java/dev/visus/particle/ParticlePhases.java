package dev.visus.particle;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Which particle render types draw in which phase (architecture §5 L5, bug V-10).
 *
 * <p>With a pack's {@code particles.ordering = mixed} (the default), Oculus draws particles twice per frame: an opaque
 * phase before translucent terrain and a translucent phase after it. Its opaque filter was "every type except
 * {@code PARTICLE_SHEET_TRANSLUCENT}" while its translucent filter was "every type except the known opaque sheets", so
 * every modded type (and vanilla's {@code TERRAIN_SHEET}) was drawn in both phases: twice the cost, double-blended,
 * and once with the opaque particle program before translucent terrain.
 *
 * <p>Here each type draws in exactly one phase. Known opaque sheets draw in the opaque phase. Every other type draws
 * in the translucent phase, which is where vanilla draws all particles, until it has been seen once. The first time an
 * unknown (modded) type is drawn, the caller reports the blend and depth-write state its {@code begin()} set up
 * ({@link #learn}); a type that draws without blending and writes depth is opaque and moves to the opaque phase from
 * the next frame on.
 *
 * <p>The order inside a phase is not decided here. Forge keeps {@code ParticleEngine.particles} in a {@code TreeMap}
 * sorted by {@code ForgeHooksClient.makeParticleRenderTypeComparator}, and filtering a view of it keeps that order.
 *
 * <p>Render thread only. Generic over the type so it can be tested without Minecraft classes.
 */
public final class ParticlePhases<T> {
	public enum Kind { OPAQUE, TRANSLUCENT }

	private final Set<T> knownOpaque;
	private final Set<T> known;
	private final Map<T, Kind> learned = new IdentityHashMap<>();

	/**
	 * @param knownOpaque types that are always drawn in the opaque phase
	 * @param known       every type whose phase is fixed (the vanilla types); all others are learned
	 */
	public ParticlePhases(Collection<T> knownOpaque, Collection<T> known) {
		this.knownOpaque = identitySet(knownOpaque);
		this.known = identitySet(known);
		this.known.addAll(this.knownOpaque);
	}

	private static <T> Set<T> identitySet(Collection<T> items) {
		Set<T> set = Collections.newSetFromMap(new IdentityHashMap<>());
		set.addAll(items);
		return set;
	}

	public boolean isOpaque(T type) {
		return knownOpaque.contains(type) || learned.get(type) == Kind.OPAQUE;
	}

	public boolean drawsInOpaquePhase(T type) {
		return isOpaque(type);
	}

	public boolean drawsInTranslucentPhase(T type) {
		return !isOpaque(type);
	}

	/** @return true for a type that is neither known nor learned yet: probe its state after its {@code begin()}. */
	public boolean needsProbe(T type) {
		return !known.contains(type) && !learned.containsKey(type);
	}

	/**
	 * Records the render state a type's {@code begin()} left behind.
	 *
	 * @return the kind it was classified as
	 */
	public Kind learn(T type, boolean blendEnabled, boolean depthWrite) {
		Kind kind = !blendEnabled && depthWrite ? Kind.OPAQUE : Kind.TRANSLUCENT;
		learned.put(type, kind);
		return kind;
	}

	/** Forgets learned types, for a resource reload that may have replaced them. */
	public void clearLearned() {
		learned.clear();
	}

	public int learnedCount() {
		return learned.size();
	}
}
