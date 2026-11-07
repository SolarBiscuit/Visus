package dev.visus.compat.geckolib;

/**
 * Notices when rendering switches between the shadow pass and the main pass (architecture §4.7, bug V-09).
 *
 * <p>GeckoLib 4's {@code GeoModel.handleAnimations} remembers the last instance it posed ({@code lastRenderedInstance}).
 * When the same instance renders again with the same frame time it returns before posing the bones, on the
 * assumption that nothing changed them. With a shader pack every entity renders twice per frame, shadow pass first,
 * so the main-pass render of a mob returned early and skipped {@code tickAnimation} and {@code setCustomAnimations}.
 *
 * <p>Visus forgets {@code lastRenderedInstance} whenever the pass changes. Animation time still advances once per
 * frame (the second render sees the same frame time and does not update it); both passes pose the bones.
 *
 * <p>One tracker per model. Render thread only.
 */
public final class RenderPassTracker {
	private static final int NONE = -1;
	private static final int MAIN = 0;
	private static final int SHADOW = 1;

	private int lastPass = NONE;

	/** @return true when this render is in a different pass from the previous one (or is the first) */
	public boolean passChanged(boolean shadowPass) {
		int pass = shadowPass ? SHADOW : MAIN;
		if (pass == lastPass) {
			return false;
		}
		lastPass = pass;
		return true;
	}
}
