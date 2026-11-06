package net.irisshaders.batchedentityrendering.impl;

public interface Groupable {
	void startGroup();

	boolean maybeStartGroup();

	void endGroup();

	/**
	 * Visus V-01: closes any group still open from an earlier frame. Called at the start of each level render.
	 */
	default void resetGroupState() {
	}
}
