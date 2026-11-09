package dev.visus.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-06b: a blend lock leaked by an apply() without clear() (Flywheel's post-link setup) dropped the blend calls of
 * every later mod draw, Forge render-stage handlers included.
 */
class StateLockGuardTest {
	/** The same rules as Oculus's BlendModeStorage: while locked, enable/disable is only remembered. */
	static final class FakeBlend implements StateLockGuard.Lock {
		boolean locked;
		boolean enabled;
		boolean deferredEnabled;

		/** ExtendedShader.apply() with a blend override. */
		void programApply() {
			deferredEnabled = enabled;
			enabled = false;
			locked = true;
		}

		/** ExtendedShader.clear(). */
		void programClear() {
			release();
		}

		/** GlStateManager._enableBlend() with MixinGlStateManager_BlendOverride in front of it. */
		void modEnableBlend() {
			if (locked) {
				deferredEnabled = true;
			} else {
				enabled = true;
			}
		}

		@Override
		public boolean isLocked() {
			return locked;
		}

		@Override
		public void release() {
			if (!locked) {
				return;
			}
			locked = false;
			enabled = deferredEnabled;
		}
	}

	@Test
	void leakedLockDropsModBlendWithoutTheGuard() {
		FakeBlend blend = new FakeBlend();
		blend.programApply(); // Flywheel post-link bind(), then GlProgram.unbind() with no clear()

		blend.modEnableBlend(); // a RenderLevelStageEvent handler
		assertFalse(blend.enabled, "the bug: the handler's blend call is dropped");
	}

	@Test
	void guardReleasesALeakedLockBeforeTheStage() {
		FakeBlend blend = new FakeBlend();
		StateLockGuard guard = new StateLockGuard(blend);
		blend.programApply();

		assertEquals(1, guard.releaseLeaked());
		blend.modEnableBlend();
		assertTrue(blend.enabled);
		assertFalse(blend.locked);
	}

	@Test
	void pairedApplyAndClearIsLeftAlone() {
		FakeBlend blend = new FakeBlend();
		StateLockGuard guard = new StateLockGuard(blend);
		blend.programApply();
		blend.programClear();

		assertEquals(0, guard.releaseLeaked());
	}

	@Test
	void releaseAppliesWhatWasAskedForWhileLocked() {
		FakeBlend blend = new FakeBlend();
		StateLockGuard guard = new StateLockGuard(blend);
		blend.programApply();
		blend.modEnableBlend(); // remembered only

		guard.releaseLeaked();
		assertTrue(blend.enabled);
	}

	@Test
	void masksNameEachLockAndReportOnce() {
		FakeBlend blend = new FakeBlend();
		FakeBlend depthColor = new FakeBlend();
		StateLockGuard guard = new StateLockGuard(blend, depthColor);

		depthColor.programApply();
		int released = guard.releaseLeaked();
		assertEquals(0b10, released);
		assertEquals(0b10, guard.firstReports(released));

		depthColor.programApply();
		blend.programApply();
		released = guard.releaseLeaked();
		assertEquals(0b11, released);
		assertEquals(0b01, guard.firstReports(released), "depth/colour was already reported");
	}
}
