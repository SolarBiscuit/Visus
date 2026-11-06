package dev.visus.gl;

import org.lwjgl.opengl.GL30C;

/**
 * Works out the sized depth format the driver actually allocated (bug ledger V-06).
 *
 * <p>Vanilla allocates the main depth texture as unsized {@code GL_DEPTH_COMPONENT}, so the driver picks the real
 * format. Oculus allocated {@code depthtex1}/{@code depthtex2} from the requested format and a different pixel
 * type, which can make the driver pick a different sized format. {@code glCopyImageSubData} then fails with
 * "depth formats do not match", and the copied depth is wrong. Visus reads back the depth and stencil bit counts
 * and allocates the copies in exactly the source's sized format.
 */
public final class DepthFormats {
	/** {@code GL_UNSIGNED_NORMALIZED}, the value of {@code GL_TEXTURE_DEPTH_TYPE} for fixed-point depth. */
	public static final int UNSIGNED_NORMALIZED = 0x8C17;

	private DepthFormats() {
	}

	/** @return true if {@code internalFormat} is a sized depth or depth-stencil format */
	public static boolean isSized(int internalFormat) {
		return switch (internalFormat) {
			case GL30C.GL_DEPTH_COMPONENT16, GL30C.GL_DEPTH_COMPONENT24, GL30C.GL_DEPTH_COMPONENT32,
				GL30C.GL_DEPTH_COMPONENT32F, GL30C.GL_DEPTH24_STENCIL8, GL30C.GL_DEPTH32F_STENCIL8 -> true;
			default -> false;
		};
	}

	/**
	 * Maps the queried {@code GL_TEXTURE_DEPTH_SIZE}, {@code GL_TEXTURE_DEPTH_TYPE} and
	 * {@code GL_TEXTURE_STENCIL_SIZE} to a sized internal format.
	 *
	 * @return the sized format, or -1 if the combination is not a standard one
	 */
	public static int fromBits(int depthBits, int depthType, int stencilBits) {
		boolean isFloat = depthType == GL30C.GL_FLOAT;

		if (stencilBits > 0) {
			if (depthBits == 24 && !isFloat) {
				return GL30C.GL_DEPTH24_STENCIL8;
			}

			if (depthBits == 32 && isFloat) {
				return GL30C.GL_DEPTH32F_STENCIL8;
			}

			return -1;
		}

		return switch (depthBits) {
			case 16 -> isFloat ? -1 : GL30C.GL_DEPTH_COMPONENT16;
			case 24 -> isFloat ? -1 : GL30C.GL_DEPTH_COMPONENT24;
			case 32 -> isFloat ? GL30C.GL_DEPTH_COMPONENT32F : GL30C.GL_DEPTH_COMPONENT32;
			default -> -1;
		};
	}
}
