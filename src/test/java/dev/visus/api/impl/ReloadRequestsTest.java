package dev.visus.api.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NeoVoxy #30: a reload requested from inside the pipeline constructor ran there and then, re-entering the
 * constructor. Requests now run once, from the between-frames queue.
 */
class ReloadRequestsTest {
	private final Queue<Runnable> betweenFrames = new ArrayDeque<>();
	private final List<String> events = new ArrayList<>();

	private void runFrameBoundary() {
		while (!betweenFrames.isEmpty()) betweenFrames.poll().run();
	}

	@Test
	void aRequestInsideConstructionRunsAfterIt() {
		ReloadRequests reloads = new ReloadRequests(betweenFrames::add, () -> events.add("reload"));

		// A fake pipeline constructor that asks for a reload halfway through (what NeoVoxy's failure path did).
		events.add("constructor start");
		assertTrue(reloads.request("LOD pipeline failed"));
		events.add("constructor end");

		assertEquals(List.of("constructor start", "constructor end"), events, "nothing ran inside the constructor");
		runFrameBoundary();
		assertEquals(List.of("constructor start", "constructor end", "reload"), events);
	}

	@Test
	void manyRequestsBeforeTheBoundaryReloadOnce() {
		ReloadRequests reloads = new ReloadRequests(betweenFrames::add, () -> events.add("reload"));
		assertTrue(reloads.request("first"));
		assertFalse(reloads.request("second"));
		assertFalse(reloads.request("third"));
		assertEquals(2, reloads.joined());
		assertEquals("first", reloads.firstReason());
		runFrameBoundary();
		assertEquals(List.of("reload"), events);
		assertFalse(reloads.isPending());
	}

	@Test
	void aRequestDuringTheReloadSchedulesTheNextOne() {
		ReloadRequests[] holder = new ReloadRequests[1];
		holder[0] = new ReloadRequests(betweenFrames::add, () -> {
			events.add("reload");
			if (events.size() == 1) holder[0].request("from a reload listener");
		});
		holder[0].request("start");
		runFrameBoundary();
		assertEquals(List.of("reload", "reload"), events, "a listener's request is honoured once, not lost or looped");
	}
}
