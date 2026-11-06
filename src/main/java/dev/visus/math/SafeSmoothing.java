package dev.visus.math;

/**
 * One step of exponential smoothing that cannot latch NaN or Inf (bug ledger V-02).
 *
 * <p>For finite input, a finite accumulator, a finite decay constant and {@code dt >= 0}, the result is
 * bit-identical to Oculus 1.8.0's {@code SmoothedFloat}: {@code lerp(acc, x, 1 - exp(-k * dt))}. Only the inputs
 * that used to produce NaN change:
 * <ul>
 *   <li>A non-finite sample is ignored: the accumulator is kept.</li>
 *   <li>A non-finite accumulator is replaced by the sample, which recovers the uniform on the next good value.</li>
 *   <li>An infinite or NaN decay constant (half-life 0 or NaN) passes the sample through. Oculus computed
 *       {@code -Inf * 0 = NaN} on a frame with {@code dt = 0}.</li>
 *   <li>A NaN or negative {@code dt} counts as 0. Oculus overshot on a negative {@code dt}. An infinite {@code dt}
 *       counts as {@link Float#MAX_VALUE}, so a zero decay constant keeps the accumulator instead of giving NaN.</li>
 * </ul>
 */
public final class SafeSmoothing {
	private SafeSmoothing() {
	}

	/**
	 * @param accumulator the previous smoothed value
	 * @param sample      the new unsmoothed value
	 * @param decay       the decay constant k in e^(-k t), from the half-life
	 * @param dt          the time since the previous step, in seconds
	 * @return the new smoothed value
	 */
	public static float step(float accumulator, float sample, float decay, float dt) {
		if (!Float.isFinite(sample)) {
			return accumulator;
		}

		if (!Float.isFinite(accumulator) || !Float.isFinite(decay)) {
			return sample;
		}

		if (!(dt >= 0.0f)) {
			dt = 0.0f;
		} else if (dt == Float.POSITIVE_INFINITY) {
			// k = 0 (infinite half-life) times an infinite dt is NaN; the largest finite dt gives the intended limit.
			dt = Float.MAX_VALUE;
		}

		// Same expression and evaluation order as Oculus, so finite results do not change by a single bit.
		float smoothingFactor = 1.0f - (float) Math.exp(-decay * dt);
		return (1 - smoothingFactor) * accumulator + smoothingFactor * sample;
	}
}
