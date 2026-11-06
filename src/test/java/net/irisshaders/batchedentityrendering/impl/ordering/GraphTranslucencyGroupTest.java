package net.irisshaders.batchedentityrendering.impl.ordering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V-01: Oculus 1.8.0 threw {@code IllegalStateException("Already in a group")} on a nested start and
 * {@code "Not in a group"} on an unmatched end. Both fail against the old class.
 */
class GraphTranslucencyGroupTest {
	@Test
	void nestedStartGroupDoesNotThrow() {
		GraphTranslucencyRenderOrderManager m = new GraphTranslucencyRenderOrderManager();
		m.startGroup();
		assertDoesNotThrow(m::startGroup);
		assertEquals(2, m.getGroupDepth());
		m.endGroup();
		m.endGroup();
		assertEquals(0, m.getGroupDepth());
	}

	@Test
	void unmatchedEndGroupDoesNotThrow() {
		GraphTranslucencyRenderOrderManager m = new GraphTranslucencyRenderOrderManager();
		assertDoesNotThrow(m::endGroup);
		assertEquals(0, m.getGroupDepth());
	}

	@Test
	void rendererThatThrowsMidGroupHealsAtFrameStart() {
		GraphTranslucencyRenderOrderManager m = new GraphTranslucencyRenderOrderManager();
		m.startGroup();
		try {
			throw new RuntimeException("renderer failed before endGroup");
		} catch (RuntimeException ignored) {
			// a mod caught it and carried on; endGroup never ran
		}

		m.resetGroupState();
		assertEquals(0, m.getGroupDepth());
		assertDoesNotThrow(m::startGroup);
		m.endGroup();
	}

	@Test
	void maybeStartGroupKeepsItsContract() {
		GraphTranslucencyRenderOrderManager m = new GraphTranslucencyRenderOrderManager();
		assertTrue(m.maybeStartGroup());
		assertFalse(m.maybeStartGroup());
		m.endGroup();
		assertTrue(m.maybeStartGroup());
		m.endGroup();
	}
}
