package dev.visus.math;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V-02. */
class SafeSmoothingTest {
	private static final float[] SPECIALS = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.0f, -0.0f,
		Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, 1.0f, -1.0f};

	/** Oculus 1.8.0 SmoothedFloat#update, verbatim arithmetic. */
	private static float oculus(float acc, float x, float k, float dt) {
		float smoothingFactor = 1.0f - (float) Math.exp(-k * dt);
		return (1 - smoothingFactor) * acc + smoothingFactor * x;
	}

	private static float pick(Random r) {
		return r.nextInt(4) == 0 ? SPECIALS[r.nextInt(SPECIALS.length)] : (r.nextFloat() - 0.5f) * 2000.0f;
	}

	@Test
	void halfLifeZeroOnAZeroTimeStepDoesNotProduceNaN() {
		float k = (float) (1.0f / (0.0f / Math.log(2.0))); // computeDecay(0) = +Inf
		assertTrue(Float.isNaN(oculus(5.0f, 7.0f, k, 0.0f)), "Oculus latched NaN here");
		assertEquals(7.0f, SafeSmoothing.step(5.0f, 7.0f, k, 0.0f));
	}

	@Test
	void nanSampleDoesNotLatch() {
		float acc = SafeSmoothing.step(3.0f, Float.NaN, 2.0f, 0.016f);
		assertEquals(3.0f, acc);
		acc = SafeSmoothing.step(acc, 4.0f, 2.0f, 0.016f);
		assertTrue(Float.isFinite(acc));
	}

	@Test
	void poisonedAccumulatorRecovers() {
		assertEquals(4.0f, SafeSmoothing.step(Float.NaN, 4.0f, 2.0f, 0.016f));
		assertEquals(4.0f, SafeSmoothing.step(Float.POSITIVE_INFINITY, 4.0f, 2.0f, 0.016f));
	}

	@Test
	void negativeOrNanTimeStepDoesNotOvershoot() {
		assertEquals(3.0f, SafeSmoothing.step(3.0f, 10.0f, 2.0f, -1.0f));
		assertEquals(3.0f, SafeSmoothing.step(3.0f, 10.0f, 2.0f, Float.NaN));
	}

	@Test
	void finiteInputsGiveFiniteOutputs() {
		Random r = new Random(0x5715);

		for (int i = 0; i < 200_000; i++) {
			float acc = pick(r);
			float x = pick(r);
			float k = Math.abs(pick(r));
			float dt = pick(r);
			float out = SafeSmoothing.step(acc, x, k, dt);

			if (Float.isFinite(acc) || Float.isFinite(x)) {
				// Overflow of a lerp between two finite values near Float.MAX_VALUE is the only way to leave the range.
				boolean nearMax = Math.abs(acc) > 1e37f || Math.abs(x) > 1e37f;
				assertTrue(Float.isFinite(out) || nearMax, () -> "acc=" + acc + " x=" + x + " k=" + k + " dt=" + dt + " -> " + out);
			}
		}
	}

	@Test
	void normalInputsAreBitIdenticalToOculus() {
		Random r = new Random(42);

		for (int i = 0; i < 200_000; i++) {
			float acc = (r.nextFloat() - 0.5f) * 200.0f;
			float x = (r.nextFloat() - 0.5f) * 200.0f;
			float k = r.nextFloat() * 50.0f;
			float dt = r.nextFloat() * 0.25f;
			assertEquals(Float.floatToRawIntBits(oculus(acc, x, k, dt)), Float.floatToRawIntBits(SafeSmoothing.step(acc, x, k, dt)));
		}
	}
}
