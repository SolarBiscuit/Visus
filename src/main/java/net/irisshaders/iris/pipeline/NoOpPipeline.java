package net.irisshaders.iris.pipeline;

/**
 * Visus V-05: a stateless pipeline used in place of null (the null-object pattern).
 *
 * <p>It behaves like {@link VanillaRenderingPipeline} but never changes world rendering settings, so returning it
 * from an accessor has no side effects. Hooks that run when no pipeline is prepared call it and do nothing.
 */
public final class NoOpPipeline extends VanillaRenderingPipeline {
	public static final NoOpPipeline INSTANCE = new NoOpPipeline();

	private NoOpPipeline() {
		super(false);
	}
}
