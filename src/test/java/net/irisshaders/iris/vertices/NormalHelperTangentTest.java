package net.irisshaders.iris.vertices;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V-03: degenerate UVs must not give a zero or non-unit tangent; normal UVs must give the Oculus result. */
class NormalHelperTangentTest {
	private static final float QUANT = 2.0f / 127.0f;

	/** Oculus 1.8.0 NormalHelper#computeTangent(18 floats), same arithmetic. */
	private static int oculus(float normalX, float normalY, float normalZ, float x0, float y0, float z0, float u0, float v0,
							  float x1, float y1, float z1, float u1, float v1,
							  float x2, float y2, float z2, float u2, float v2) {
		float edge1x = x1 - x0;
		float edge1y = y1 - y0;
		float edge1z = z1 - z0;
		float edge2x = x2 - x0;
		float edge2y = y2 - y0;
		float edge2z = z2 - z0;
		float deltaU1 = u1 - u0;
		float deltaV1 = v1 - v0;
		float deltaU2 = u2 - u0;
		float deltaV2 = v2 - v0;
		float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
		float f = fdenom == 0.0 ? 1.0f : 1.0f / fdenom;
		float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
		float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
		float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
		float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
		tangentx *= tcoeff;
		tangenty *= tcoeff;
		tangentz *= tcoeff;
		float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
		float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
		float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
		float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
		bitangentx *= bitcoeff;
		bitangenty *= bitcoeff;
		bitangentz *= bitcoeff;
		float pbitangentx = tangenty * normalZ - tangentz * normalY;
		float pbitangenty = tangentz * normalX - tangentx * normalZ;
		float pbitangentz = tangentx * normalY - tangenty * normalX;
		float dot = (bitangentx * pbitangentx) + (bitangenty * pbitangenty) + (bitangentz * pbitangentz);
		float tangentW = dot < 0 ? -1.0F : 1.0F;
		return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
	}

	private static float rsqrt(float value) {
		return value == 0.0f ? 1.0f : (float) (1.0 / Math.sqrt(value));
	}

	private static void assertUsableTangent(int packed, float nx, float ny, float nz) {
		float tx = NormI8.unpackX(packed);
		float ty = NormI8.unpackY(packed);
		float tz = NormI8.unpackZ(packed);
		float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
		float nlen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		assertTrue(Math.abs(len - 1.0f) < 2 * QUANT, "tangent length " + len);
		assertTrue(Math.abs((tx * nx + ty * ny + tz * nz) / nlen) < 2 * QUANT, "tangent not perpendicular to normal");
	}

	/** A triangle in the XZ plane with the given UVs. */
	private static int tangent(float nx, float ny, float nz, float u0, float v0, float u1, float v1, float u2, float v2) {
		return NormalHelper.computeTangent(nx, ny, nz, 0, 0, 0, u0, v0, 1, 0, 0, u1, v1, 0, 0, 1, u2, v2);
	}

	@Test
	void identicalUvsGiveAUnitTangent() {
		int old = oculus(0, 1, 0, 0, 0, 0, .5f, .5f, 1, 0, 0, .5f, .5f, 0, 0, 1, .5f, .5f);
		assertEquals(0, old & 0xFFFFFF, "Oculus packed a zero tangent here");
		assertUsableTangent(tangent(0, 1, 0, .5f, .5f, .5f, .5f, .5f, .5f), 0, 1, 0);
	}

	@Test
	void collinearUvsGiveAUnitTangent() {
		assertUsableTangent(tangent(0, 1, 0, 0, 0, .25f, .25f, .5f, .5f), 0, 1, 0);
	}

	@Test
	void singleTexelUvsGiveAUnitTangent() {
		float t = 1.0f / 1024.0f;
		assertUsableTangent(tangent(0, 1, 0, t, t, t, t, t, t), 0, 1, 0);
	}

	@Test
	void fallbackHandlesEveryNormal() {
		assertUsableTangent(NormalHelper.fallbackTangent(0, 0, -1), 0, 0, -1);
		assertUsableTangent(NormalHelper.fallbackTangent(0, 0, 1), 0, 0, 1);
		assertUsableTangent(NormalHelper.fallbackTangent(0.577f, 0.577f, 0.577f), 0.577f, 0.577f, 0.577f);
		assertUsableTangent(NormalHelper.fallbackTangent(0, 3, 0), 0, 1, 0);
		assertEquals(1.0f, NormI8.unpackX(NormalHelper.fallbackTangent(0, 0, 0)), QUANT);
		assertEquals(1.0f, NormI8.unpackX(NormalHelper.fallbackTangent(Float.NaN, 0, 0)), QUANT);
	}

	@Test
	void normalInputsAreIdenticalToOculus() {
		Random r = new Random(7);
		int compared = 0;

		for (int i = 0; i < 100_000; i++) {
			float[] v = new float[18];

			for (int j = 0; j < v.length; j++) {
				v[j] = r.nextFloat() * 2 - 1;
			}

			int expected = oculus(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17]);

			if ((expected & 0xFFFFFF) == 0) {
				continue; // a degenerate case, where Visus deliberately differs
			}

			int actual = NormalHelper.computeTangent(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17]);
			assertEquals(expected, actual);
			compared++;
		}

		assertTrue(compared > 99_000);
	}
}
