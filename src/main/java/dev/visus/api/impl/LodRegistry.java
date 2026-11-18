package dev.visus.api.impl;

import dev.visus.api.ReloadListener;
import dev.visus.api.lod.LodPackException;
import dev.visus.api.lod.LodPipelineListener;
import dev.visus.api.lod.LodProgramProvider;
import dev.visus.api.lod.PackSources;
import dev.visus.api.lod.UniformContributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Registered API users (V-13, architecture §4.5). Registration is thread-safe (mods register from construction and
 * setup threads); dispatch reads volatile copy-on-write arrays, so it neither locks nor allocates on the render thread.
 * No Minecraft or GL types, so it is unit-tested directly.
 */
public final class LodRegistry {
	private static final LodProgramProvider[] NO_PROVIDERS = new LodProgramProvider[0];
	private static final UniformContributor[] NO_CONTRIBUTORS = new UniformContributor[0];
	private static final LodPipelineListener[] NO_LISTENERS = new LodPipelineListener[0];
	private static final ReloadListener[] NO_RELOAD_LISTENERS = new ReloadListener[0];

	private volatile LodProgramProvider[] providers = NO_PROVIDERS;
	private volatile UniformContributor[] contributors = NO_CONTRIBUTORS;
	private volatile LodPipelineListener[] listeners = NO_LISTENERS;
	private volatile ReloadListener[] reloadListeners = NO_RELOAD_LISTENERS;
	/** Raised on every provider registration; extra source names and colour targets are recomputed when it changes. */
	private volatile int providerVersion;

	public synchronized void register(LodProgramProvider provider) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(provider.id(), "provider.id()");
		providers = append(providers, provider);
		providerVersion++;
	}

	public synchronized void register(UniformContributor contributor) {
		contributors = append(contributors, Objects.requireNonNull(contributor, "contributor"));
	}

	public synchronized void addListener(LodPipelineListener listener) {
		listeners = append(listeners, Objects.requireNonNull(listener, "listener"));
	}

	public synchronized void addReloadListener(ReloadListener listener) {
		reloadListeners = append(reloadListeners, Objects.requireNonNull(listener, "listener"));
	}

	public LodProgramProvider[] providers() {
		return providers;
	}

	public UniformContributor[] contributors() {
		return contributors;
	}

	public LodPipelineListener[] listeners() {
		return listeners;
	}

	public ReloadListener[] reloadListeners() {
		return reloadListeners;
	}

	public int providerVersion() {
		return providerVersion;
	}

	/** Every provider's extra source names, in registration order, without duplicates. */
	public List<String> extraSourceNames() {
		Set<String> names = new LinkedHashSet<>();
		for (LodProgramProvider provider : providers) {
			Collection<String> own = provider.extraSourceNames();
			if (own != null) {
				for (String name : own) {
					if (name != null && !name.isBlank()) names.add(name);
				}
			}
		}
		return List.copyOf(names);
	}

	/** The largest number of extra colour targets any provider asked for, clamped to 0..184 (colortex0..199). */
	public int extraColorTargets() {
		int max = 0;
		for (LodProgramProvider provider : providers) {
			max = Math.max(max, provider.extraColorTargets());
		}
		return Math.max(0, Math.min(184, max));
	}

	/**
	 * Calls every provider for one program set. A provider that throws (a {@link LodPackException} for bad pack files,
	 * or any runtime exception) is reported through {@code onFailure} and does not stop the others or the pack load.
	 *
	 * @return the ids of the providers that failed
	 */
	public List<String> onProgramSetBuilt(PackSources sources, BiConsumer<LodProgramProvider, Exception> onFailure) {
		List<String> failed = new ArrayList<>(0);
		for (LodProgramProvider provider : providers) {
			try {
				provider.onProgramSetBuilt(sources);
			} catch (LodPackException | RuntimeException e) {
				failed.add(provider.id());
				onFailure.accept(provider, e);
			}
		}
		return failed;
	}

	/** Runs {@code call} for every listener; one that throws is reported and the rest still run. */
	public void forEachListener(Consumer<LodPipelineListener> call, BiConsumer<Object, RuntimeException> onFailure) {
		for (LodPipelineListener listener : listeners) {
			try {
				call.accept(listener);
			} catch (RuntimeException e) {
				onFailure.accept(listener, e);
			}
		}
	}

	/** Runs every reload listener in registration order; one that throws is reported and the rest still run. */
	public void fireReloaded(boolean packInUse, BiConsumer<Object, RuntimeException> onFailure) {
		for (ReloadListener listener : reloadListeners) {
			try {
				listener.onPackReloaded(packInUse);
			} catch (RuntimeException e) {
				onFailure.accept(listener, e);
			}
		}
	}

	private static <T> T[] append(T[] array, T element) {
		T[] copy = Arrays.copyOf(array, array.length + 1);
		copy[array.length] = element;
		return copy;
	}
}
