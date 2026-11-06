package dev.visus.gl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L8: GL debug message rate limit. */
class DebugMessageLimiterTest {
	@Test
	void repeatsAreSuppressedAndSummarised() {
		AtomicLong now = new AtomicLong();
		DebugMessageLimiter l = new DebugMessageLimiter(now::get);

		for (int i = 0; i < DebugMessageLimiter.FULL_REPORTS_PER_KEY; i++) {
			assertTrue(l.shouldPrint(0x8246, 0x502));
		}

		assertFalse(l.shouldPrint(0x8246, 0x502));
		assertTrue(l.shouldPrint(0x8246, 0x503), "a different id is a different message");
		assertEquals(1, l.timesSeen(0x8246, 0x503));
		assertEquals(DebugMessageLimiter.FULL_REPORTS_PER_KEY + 1, l.timesSeen(0x8246, 0x502));
		assertNull(l.pollSummary(), "no summary before the interval");
		now.addAndGet(DebugMessageLimiter.SUMMARY_INTERVAL_NANOS);
		String summary = l.pollSummary();
		assertNotNull(summary);
		assertTrue(summary.contains("id 0x502: 1"), summary);
		assertNull(l.pollSummary(), "counts reset after a summary");
	}

	@Test
	void resetStartsCountingAgain() {
		DebugMessageLimiter l = new DebugMessageLimiter(() -> 0L);

		for (int i = 0; i <= DebugMessageLimiter.FULL_REPORTS_PER_KEY; i++) {
			l.shouldPrint(1, 2);
		}

		l.reset();
		assertTrue(l.shouldPrint(1, 2));
	}
}
