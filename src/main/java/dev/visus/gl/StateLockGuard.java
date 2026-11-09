package dev.visus.gl;

/**
 * V-06b: releases GL state locks that are still held where none can legitimately be held.
 *
 * <p>Oculus locks the blend state ({@code BlendModeStorage}) and the depth/colour masks ({@code DepthColorStorage})
 * while a pack program with a blend override draws. While a lock is held, {@code GlStateManager}'s blend and mask
 * calls are cancelled and only remembered for later. Each lock is meant to last one draw: the program's
 * {@code apply()} takes it and its {@code clear()} releases it. A caller that applies without clearing (Flywheel's
 * post-link setup did this) leaves the lock held. Every mod draw after that, Forge render-stage handlers included,
 * then has its blend and mask calls dropped until some later pack draw happens to release the lock.
 *
 * <p>A Forge render-stage dispatch is always between draws, so a lock still held there was leaked. This guard
 * releases it, which applies the state the dropped calls asked for, and reports each lock once.
 *
 * <p>Render thread only. No allocation after construction.
 */
public final class StateLockGuard {
	/** One lockable piece of GL state. */
	public interface Lock {
		boolean isLocked();

		/** Unlocks and applies the state that was asked for while locked. */
		void release();
	}

	private final Lock[] locks;
	private final boolean[] reported;

	public StateLockGuard(Lock... locks) {
		this.locks = locks.clone();
		this.reported = new boolean[locks.length];
	}

	/**
	 * Releases every lock that is still held.
	 *
	 * @return a bit mask of the locks released by this call (bit i is lock i), 0 when nothing had leaked
	 */
	public int releaseLeaked() {
		int released = 0;
		for (int i = 0; i < locks.length; i++) {
			if (locks[i].isLocked()) {
				locks[i].release();
				released |= 1 << i;
			}
		}
		return released;
	}

	/**
	 * Of the locks in {@code released}, those not reported before; marks them reported.
	 *
	 * @return a bit mask of the locks to report now
	 */
	public int firstReports(int released) {
		int fresh = 0;
		for (int i = 0; i < locks.length; i++) {
			if ((released & (1 << i)) != 0 && !reported[i]) {
				reported[i] = true;
				fresh |= 1 << i;
			}
		}
		return fresh;
	}
}
