package dev.visus.gl;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.function.LongSupplier;

/**
 * Rate limit for GL debug output (architecture L8).
 *
 * <p>Oculus printed every debug message in full, with a stack trace. A broken state repeated every frame could write
 * thousands of identical blocks a second and stall the render thread on logging. This limiter lets the first
 * {@link #FULL_REPORTS_PER_KEY} messages of each (source, id) through. It counts later ones and reports the counts in
 * one summary per {@link #SUMMARY_INTERVAL_NANOS}.
 *
 * <p>Called from the GL debug callback, which runs on the thread that owns the context. Not thread-safe.
 */
public final class DebugMessageLimiter {
	public static final int FULL_REPORTS_PER_KEY = 5;
	public static final long SUMMARY_INTERVAL_NANOS = 60_000_000_000L;

	private final Long2IntOpenHashMap seen = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap suppressedSinceSummary = new Long2IntOpenHashMap();
	private final LongSupplier clock;
	private long lastSummary;

	public DebugMessageLimiter() {
		this(System::nanoTime);
	}

	public DebugMessageLimiter(LongSupplier clock) {
		this.clock = clock;
		this.lastSummary = clock.getAsLong();
	}

	public static long key(int source, int id) {
		return ((long) source << 32) | (id & 0xFFFFFFFFL);
	}

	/**
	 * Records one message.
	 *
	 * @return true if the caller should print it in full
	 */
	public boolean shouldPrint(int source, int id) {
		long key = key(source, id);
		int count = seen.addTo(key, 1) + 1;

		if (count <= FULL_REPORTS_PER_KEY) {
			return true;
		}

		suppressedSinceSummary.addTo(key, 1);
		return false;
	}

	/** @return how many times this (source, id) has been recorded so far */
	public int timesSeen(int source, int id) {
		return seen.get(key(source, id));
	}

	/**
	 * Returns a summary line once per interval while messages are being suppressed, so the log shows that the problem
	 * is still happening.
	 *
	 * @return the summary, or null if none is due
	 */
	public String pollSummary() {
		long now = clock.getAsLong();

		if (suppressedSinceSummary.isEmpty() || now - lastSummary < SUMMARY_INTERVAL_NANOS) {
			return null;
		}

		lastSummary = now;
		StringBuilder sb = new StringBuilder("[GL] Repeated debug messages suppressed in the last minute:");

		for (Long2IntMap.Entry e : suppressedSinceSummary.long2IntEntrySet()) {
			long k = e.getLongKey();
			sb.append(String.format(" [source 0x%X id 0x%X: %d]", (int) (k >>> 32), (int) k, e.getIntValue()));
		}

		suppressedSinceSummary.clear();
		return sb.toString();
	}

	public void reset() {
		seen.clear();
		suppressedSinceSummary.clear();
		lastSummary = clock.getAsLong();
	}
}
