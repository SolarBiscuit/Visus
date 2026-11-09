package dev.visus.gl;

import dev.visus.Visus;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.blending.DepthColorStorage;

/**
 * V-06b: Oculus's two GL state locks behind a {@link StateLockGuard}. Called at the head of every Forge render-stage
 * dispatch, where no lock can legitimately be held.
 */
public final class GlStateLocks {
	private static final String[] NAMES = {"blend", "depth/colour mask"};

	private static final StateLockGuard GUARD = new StateLockGuard(
		new StateLockGuard.Lock() {
			@Override
			public boolean isLocked() {
				return BlendModeStorage.isBlendLocked();
			}

			@Override
			public void release() {
				BlendModeStorage.restoreBlend();
			}
		},
		new StateLockGuard.Lock() {
			@Override
			public boolean isLocked() {
				return DepthColorStorage.isDepthColorLocked();
			}

			@Override
			public void release() {
				DepthColorStorage.unlockDepthColor();
			}
		});

	private GlStateLocks() {
	}

	/** Releases leaked locks before a Forge render stage; logs each kind of lock the first time it leaks. */
	public static void releaseLeakedBeforeStage(Object stage) {
		int released = GUARD.releaseLeaked();
		if (released == 0) {
			return;
		}
		int fresh = GUARD.firstReports(released);
		for (int i = 0; i < NAMES.length; i++) {
			if ((fresh & (1 << i)) != 0) {
				Visus.LOGGER.warn("[V-06b] The {} lock was still held at render stage {}; released it so mod "
					+ "rendering gets its own state (logged once)", NAMES[i], stage);
			}
		}
	}
}
