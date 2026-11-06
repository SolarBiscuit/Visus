package net.irisshaders.iris.shadows.frustum.advanced;

import org.joml.Matrix4f;
import org.joml.Vector4f;

public class BaseClippingPlanes {
	/**
	 * Visus V-04: a plane is degenerate when its normal (xyz) has no length, or it contains NaN. That happens for the
	 * far plane of an infinite-far projection, where far - w is (0, 0, 0, c). Oculus kept such planes, so the back-plane
	 * and edge-plane tests ran on a zero or NaN normal. Callers skip them; with a finite projection none is degenerate,
	 * and culling is unchanged.
	 */
	// After the 4D normalisation in transform(), xyz length^2 is about 1 / (1 + d^2) for a plane at distance d. A plane
	// at 1e5 blocks (Distant Horizons range) is about 1e-10, so the threshold sits far below any real plane and only
	// catches zero normals and rounding noise.
	private static final float DEGENERATE_NORMAL_LENGTH_SQ = 1.0e-16f;

	private final Vector4f[] planes = new Vector4f[6];
	private final boolean[] degenerate = new boolean[6];

	public BaseClippingPlanes(Matrix4f playerView, Matrix4f playerProjection) {
		this.init(playerView, playerProjection);
	}

	private static Vector4f transform(Matrix4f transform, float x, float y, float z) {
		Vector4f vector4f = new Vector4f(x, y, z, 1.0F);
		vector4f.mul(transform);
		vector4f.normalize();

		return vector4f;
	}

	private void init(Matrix4f view, Matrix4f projection) {
		// Transform = Transpose(Projection x View)

		Matrix4f transform = new Matrix4f(projection);
		transform.mul(view);
		transform.transpose();

		planes[0] = transform(transform, -1, 0, 0);
		planes[1] = transform(transform, 1, 0, 0);
		planes[2] = transform(transform, 0, -1, 0);
		planes[3] = transform(transform, 0, 1, 0);
		// FAR clipping plane
		planes[4] = transform(transform, 0, 0, -1);
		// NEAR clipping plane
		planes[5] = transform(transform, 0, 0, 1);

		for (int i = 0; i < planes.length; i++) {
			degenerate[i] = isDegenerate(planes[i]);
		}
	}

	public static boolean isDegenerate(Vector4f plane) {
		float lengthSq = plane.x() * plane.x() + plane.y() * plane.y() + plane.z() * plane.z();
		return !(lengthSq > DEGENERATE_NORMAL_LENGTH_SQ) || !Float.isFinite(lengthSq) || !Float.isFinite(plane.w());
	}

	/** Visus V-04: true if plane {@code index} has no usable normal and must not be used for culling. */
	public boolean isDegenerate(int index) {
		return degenerate[index];
	}

	public Vector4f[] getPlanes() {
		return planes;
	}
}
