package net.irisshaders.batchedentityrendering.impl.ordering;

import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.minecraft.client.renderer.RenderType;

import java.util.List;

public interface RenderOrderManager {
	void begin(RenderType type);

	void startGroup();

	boolean maybeStartGroup();

	void endGroup();

	/**
	 * Visus V-01: closes any group still open from an earlier frame. Called at the start of each level render.
	 */
	default void resetGroupState() {
	}

	void reset();

	void resetType(TransparencyType type);

	List<RenderType> getRenderOrder();
}
