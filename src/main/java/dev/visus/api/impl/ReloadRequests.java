package dev.visus.api.impl;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Coalesces {@code VisusApi.requestReload} calls (fixes NeoVoxy #30: a reload re-entered from inside the pipeline
 * constructor). A request never runs on the caller's stack: it schedules one task on the main-thread queue, which runs
 * between frames, and every further request before that task runs joins it. No Minecraft types, so it is unit-tested
 * with a manual executor.
 */
public final class ReloadRequests {
	private final Executor betweenFrames;
	private final Runnable reload;
	private boolean pending;
	private String firstReason;
	private int joined;

	/**
	 * @param betweenFrames runs a task on the main thread between frames ({@code Minecraft::tell} in the game)
	 * @param reload        the reload itself
	 */
	public ReloadRequests(Executor betweenFrames, Runnable reload) {
		this.betweenFrames = Objects.requireNonNull(betweenFrames);
		this.reload = Objects.requireNonNull(reload);
	}

	/** @return true if this request scheduled the reload, false if it joined one already scheduled */
	public boolean request(String reason) {
		synchronized (this) {
			if (pending) {
				joined++;
				return false;
			}
			pending = true;
			firstReason = reason;
			joined = 0;
		}
		betweenFrames.execute(this::run);
		return true;
	}

	private void run() {
		synchronized (this) {
			pending = false;
		}
		reload.run();
	}

	public synchronized boolean isPending() {
		return pending;
	}

	/** The reason given by the request that scheduled the pending reload. */
	public synchronized String firstReason() {
		return firstReason;
	}

	/** How many later requests joined the pending reload. */
	public synchronized int joined() {
		return joined;
	}
}
