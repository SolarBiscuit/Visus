package dev.visus.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupDepthTest {
	@Test
	void nestedEnterOnlyOpensOnce() {
		GroupDepth d = new GroupDepth();
		assertTrue(d.enter());
		assertFalse(d.enter());
		assertFalse(d.exit());
		assertTrue(d.exit());
		assertFalse(d.isInGroup());
	}

	@Test
	void unmatchedExitIsIgnored() {
		GroupDepth d = new GroupDepth();
		assertFalse(d.exit());
		assertEquals(0, d.depth());
		assertTrue(d.enter());
	}

	@Test
	void tryEnterOutermostRefusesWhenOpen() {
		GroupDepth d = new GroupDepth();
		assertTrue(d.tryEnterOutermost());
		assertFalse(d.tryEnterOutermost());
		assertEquals(1, d.depth());
	}

	@Test
	void resetReportsLeakedGroups() {
		GroupDepth d = new GroupDepth();
		d.enter();
		d.enter();
		assertEquals(2, d.reset());
		assertEquals(0, d.reset());
		assertTrue(d.enter());
	}
}
