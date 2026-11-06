package dev.visus.render;

/**
 * Re-entrant nesting counter for entity render groups (bug ledger V-01).
 *
 * <p>Oculus tracked groups with a single boolean, so it threw "Already in a group" when one entity render started
 * inside another (Curios, armor layers, mods that render entities from their renderers). It also threw on every
 * later frame when a renderer failed before its group ended. This counter accepts nesting, ignores an unmatched
 * exit, and can be reset at the start of each frame.
 *
 * <p>Render thread only; not thread-safe.
 */
public final class GroupDepth {
	private int depth;

	/**
	 * Enters a group.
	 *
	 * @return true if this call opened the outermost group, false if it is nested in one
	 */
	public boolean enter() {
		return depth++ == 0;
	}

	/**
	 * Enters a group only if none is open, matching the old {@code maybeStartGroup} contract.
	 *
	 * @return true if a group was opened
	 */
	public boolean tryEnterOutermost() {
		if (depth != 0) {
			return false;
		}

		depth = 1;
		return true;
	}

	/**
	 * Leaves a group. An exit with no group open is ignored.
	 *
	 * @return true if this call closed the outermost group
	 */
	public boolean exit() {
		if (depth == 0) {
			return false;
		}

		return --depth == 0;
	}

	public boolean isInGroup() {
		return depth > 0;
	}

	public int depth() {
		return depth;
	}

	/**
	 * Closes every open group. Call this at the start of a frame, when no group can legitimately be open.
	 *
	 * @return how many groups were still open, which is 0 when every renderer ended its group
	 */
	public int reset() {
		int leaked = depth;
		depth = 0;
		return leaked;
	}
}
