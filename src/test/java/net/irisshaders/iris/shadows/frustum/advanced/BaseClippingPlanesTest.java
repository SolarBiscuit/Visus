package net.irisshaders.iris.shadows.frustum.advanced;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V-04: clip planes for every depth convention, and the infinite far plane. */
class BaseClippingPlanesTest {
	private static final float FOV = (float) Math.toRadians(70);
	private static final float ASPECT = 16.0f / 9.0f;
	private static final float NEAR = 0.05f;

	private static boolean inside(BaseClippingPlanes p, float x, float y, float z) {
		Vector4f point = new Vector4f(x, y, z, 1.0f);

		for (int i = 0; i < 6; i++) {
			if (!p.isDegenerate(i) && p.getPlanes()[i].dot(point) < 0) {
				return false;
			}
		}

		return true;
	}

	private static void assertBoundsFrustum(Matrix4f projection, boolean expectFarPlane) {
		BaseClippingPlanes p = new BaseClippingPlanes(new Matrix4f(), projection);

		for (int i = 0; i < 4; i++) {
			assertFalse(p.isDegenerate(i), "side plane " + i);
		}

		assertTrue(inside(p, 0, 0, -10), "a point straight ahead is inside");
		assertTrue(inside(p, 1, -1, -50), "a point slightly off-centre is inside");
		assertFalse(inside(p, 0, 0, 10), "a point behind the camera is outside");
		assertFalse(inside(p, 100, 0, -10), "a point far to the side is outside");

		if (expectFarPlane) {
			assertFalse(inside(p, 0, 0, -5000), "a point beyond the far plane is outside");
		}
	}

	@Test
	void openGlDepthRange() {
		assertBoundsFrustum(new Matrix4f().setPerspective(FOV, ASPECT, NEAR, 1000.0f, false), true);
	}

	@Test
	void zeroToOneDepthRange() {
		assertBoundsFrustum(new Matrix4f().setPerspective(FOV, ASPECT, NEAR, 1000.0f, true), true);
	}

	@Test
	void reversedZInfiniteFar() {
		// JOML: zNear = +Inf with zZeroToOne gives the reversed-Z infinite projection used by MC 26.x.
		assertBoundsFrustum(new Matrix4f().setPerspective(FOV, ASPECT, Float.POSITIVE_INFINITY, NEAR, true), false);
	}

	@Test
	void exactInfiniteFarPlaneIsFlaggedAndIgnored() {
		// Exact infinite-far OpenGL projection (m22 = -1, m32 = -2n): far - w has a zero normal.
		Matrix4f infinite = new Matrix4f().setPerspective(FOV, ASPECT, NEAR, 1000.0f, false);
		infinite.m22(-1.0f).m32(-2.0f * NEAR);
		BaseClippingPlanes p = new BaseClippingPlanes(new Matrix4f(), infinite);
		boolean anyDegenerate = false;

		for (int i = 0; i < 6; i++) {
			anyDegenerate |= p.isDegenerate(i);
		}

		assertTrue(anyDegenerate, "the infinite far plane must be flagged");
		assertBoundsFrustum(infinite, false);
	}

	@Test
	void finiteProjectionsHaveNoDegeneratePlanes() {
		for (Matrix4f m : new Matrix4f[]{
			new Matrix4f().setPerspective(FOV, ASPECT, NEAR, 512.0f, false),
			new Matrix4f().setPerspective(FOV, ASPECT, NEAR, 200_000.0f, false),
			new Matrix4f().setOrtho(-100, 100, -100, 100, -200, 200)}) {
			BaseClippingPlanes p = new BaseClippingPlanes(new Matrix4f(), m);

			for (int i = 0; i < 6; i++) {
				assertFalse(p.isDegenerate(i), "plane " + i + " of " + m);
			}
		}
	}

	@Test
	void zeroOrNanNormalIsDegenerate() {
		assertTrue(BaseClippingPlanes.isDegenerate(new Vector4f(Float.NaN, 0, 0, 1)));
		assertTrue(BaseClippingPlanes.isDegenerate(new Vector4f(0, 0, 0, 1)));
		assertFalse(BaseClippingPlanes.isDegenerate(new Vector4f(0, 0, 1, 0)));
	}
}
