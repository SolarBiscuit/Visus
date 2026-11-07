package dev.visus.compat.geckolib;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V-09: GeckoLib bones are posed in the shadow pass and the main pass. */
class RenderPassTrackerTest {
	/**
	 * The control flow of GeckoLib 4.8.4 {@code GeoModel.handleAnimations}, reduced to what decides whether the bones
	 * get posed: an early return when the same instance renders again with the same frame time.
	 */
	static final class GeckoModel {
		long lastRenderedInstance = -1;
		double lastUpdateTime = Double.NaN;
		final RenderPassTracker passes = new RenderPassTracker();
		final boolean visusFix;
		final List<String> posed = new ArrayList<>();
		int timeUpdates;

		GeckoModel(boolean visusFix) {
			this.visusFix = visusFix;
		}

		void handleAnimations(long instanceId, double frameTime, boolean shadowPass) {
			if (visusFix && passes.passChanged(shadowPass)) {
				lastRenderedInstance = -1;
			}
			boolean isReRender = frameTime == lastUpdateTime;
			if (isReRender && instanceId == lastRenderedInstance) {
				return;
			}
			if (!isReRender) {
				lastUpdateTime = frameTime;
				timeUpdates++;
			}
			lastRenderedInstance = instanceId;
			posed.add((shadowPass ? "shadow:" : "main:") + instanceId);
		}
	}

	static void frame(GeckoModel m, double frameTime, long... instances) {
		for (long id : instances) {
			m.handleAnimations(id, frameTime, true);
		}
		for (long id : instances) {
			m.handleAnimations(id, frameTime, false);
		}
	}

	@Test
	void stockGeckoLibSkipsTheMainPassOfALoneMob() {
		GeckoModel stock = new GeckoModel(false);
		frame(stock, 1.25, 7);

		assertEquals(List.of("shadow:7"), stock.posed, "the main-pass render returned early");
	}

	@Test
	void withTheFixBothPassesArePosedAndTimeAdvancesOncePerFrame() {
		GeckoModel fixed = new GeckoModel(true);
		frame(fixed, 1.25, 7);
		frame(fixed, 1.50, 7);

		assertEquals(List.of("shadow:7", "main:7", "shadow:7", "main:7"), fixed.posed);
		assertEquals(2, fixed.timeUpdates, "one time update per frame, not per pass");
	}

	@Test
	void severalInstancesArePosedInBothPasses() {
		GeckoModel fixed = new GeckoModel(true);
		frame(fixed, 2.0, 1, 2, 3);

		assertEquals(List.of("shadow:1", "shadow:2", "shadow:3", "main:1", "main:2", "main:3"), fixed.posed);
	}

	@Test
	void trackerReportsOnlyPassChanges() {
		RenderPassTracker t = new RenderPassTracker();

		assertTrue(t.passChanged(true));
		assertFalse(t.passChanged(true));
		assertTrue(t.passChanged(false));
		assertFalse(t.passChanged(false));
		assertTrue(t.passChanged(true));
	}
}
