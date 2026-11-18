package dev.visus.api.impl;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.visus.Visus;
import dev.visus.api.lod.FrameContext;
import dev.visus.api.lod.LodPipelineListener;
import dev.visus.api.lod.PackSources;
import dev.visus.api.lod.UniformContributor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.gl.uniform.LocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.Uniform;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * The calls Oculus's own sources make into the Visus API (V-13, architecture §4.5). These sit next to the injection
 * points NeoVoxy 1.43 uses on plain Oculus, which stay in place so that it still loads on Visus. With no API user
 * registered each hook is a no-op, so pack output stays identical to Oculus 1.8.0 (T1).
 */
public final class LodHooks {
	private static final Set<String> NO_NAMES = Set.of();

	private static volatile int cachedProviderVersion = -1;
	private static volatile ImmutableList<String> cachedExtraNames = ImmutableList.of();
	/** Views of the live pipelines; weak so that a pipeline nobody destroyed through the manager can still go away. */
	private static final Map<IrisRenderingPipeline, PipelineViewImpl> VIEWS = new WeakHashMap<>();
	private static final int[] FRAME_VIEWPORT = new int[4];
	private static final FrameContext FRAME = new FrameContext() {
		@Override
		public void viewport(int[] dst) {
			dst[0] = FRAME_VIEWPORT[0];
			dst[1] = FRAME_VIEWPORT[1];
			dst[2] = FRAME_VIEWPORT[2];
			dst[3] = FRAME_VIEWPORT[3];
		}

		@Override
		public float partialTick() {
			return CapturedRenderingState.INSTANCE.getTickDelta();
		}
	};
	private static boolean checkedUnitLimits;

	private LodHooks() {
	}

	private static LodRegistry registry() {
		return VisusApiImpl.INSTANCE.registry();
	}

	// ---- discovery (ShaderPack, ProgramSet) ------------------------------------------------------------------------

	/** The providers' extra source names, recomputed only when a provider registers. */
	public static ImmutableList<String> extraSourceNames() {
		LodRegistry registry = registry();
		int version = registry.providerVersion();
		if (version != cachedProviderVersion) {
			cachedExtraNames = ImmutableList.copyOf(registry.extraSourceNames());
			cachedProviderVersion = version;
		}
		return cachedExtraNames;
	}

	/**
	 * Adds the providers' extra files found in {@code directory} or its {@code program/} subfolder to the pack's
	 * start files, so the pack loads them like its own sources. Called next to Oculus's
	 * {@code ShaderPackSourceNames.findPresentSources}.
	 *
	 * @return whether any were found (a dimension folder that holds only such files still counts as present, as with
	 *         NeoVoxy's own discovery on plain Oculus)
	 */
	public static boolean addProviderSources(ImmutableList.Builder<AbsolutePackPath> starts, Path packRoot, AbsolutePackPath directory) {
		ImmutableList<String> names = extraSourceNames();
		if (names.isEmpty()) return false;
		boolean any = false;
		any |= addPresent(starts, packRoot, directory, names);
		any |= addPresent(starts, packRoot, directory.resolve("program"), names);
		return any;
	}

	private static boolean addPresent(ImmutableList.Builder<AbsolutePackPath> starts, Path packRoot, AbsolutePackPath directory, List<String> names) {
		Path dir = directory.resolved(packRoot);
		if (!Files.isDirectory(dir)) return false;
		boolean any = false;
		for (String name : names) {
			if (Files.isRegularFile(dir.resolve(name))) {
				starts.add(directory.resolve(name));
				any = true;
			}
		}
		return any;
	}

	/** The colour targets a program set supports: Oculus's 16, plus what providers asked for. */
	public static Set<Integer> supportedRenderTargets(Set<Integer> baseline) {
		int extra = registry().extraColorTargets();
		if (extra == 0) return baseline;
		Set<Integer> targets = new LinkedHashSet<>(baseline);
		int top = baseline.size() + extra;
		for (int i = baseline.size(); i < top; i++) targets.add(i);
		return Set.copyOf(targets);
	}

	/**
	 * Runs the providers for a program set that has just been built (end of the {@code ProgramSet} constructor).
	 *
	 * @return the uniform names the providers declared through {@link PackSources#retainUniforms}
	 */
	public static Set<String> onProgramSetBuilt(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider) {
		if (registry().providers().length == 0) return NO_NAMES;
		String dir = directory.getPathString();
		String relativeDir = dir.startsWith("/") ? dir.substring(1) : dir;
		Set<String> retained = new LinkedHashSet<>();
		String packName = Iris.getCurrentPackName();
		PackSources sources = new PackSources() {
			@Override
			public String packName() {
				return packName == null ? "" : packName;
			}

			@Override
			public String directory() {
				return relativeDir;
			}

			@Override
			public Optional<String> read(String relativePath) {
				if (relativePath == null || relativePath.isBlank()) return Optional.empty();
				try {
					return Optional.ofNullable(sourceProvider.apply(directory.resolve(relativePath)));
				} catch (RuntimeException e) {
					return Optional.empty();
				}
			}

			@Override
			public void retainUniforms(Collection<String> names) {
				for (String name : names) {
					if (name != null && !name.isBlank()) retained.add(name);
				}
			}
		};
		registry().onProgramSetBuilt(sources, (provider, e) ->
			Visus.LOGGER.error("[api] LOD provider '{}' could not use pack '{}' ({}): {}. It is disabled for this program set; the pack keeps loading.",
				provider.id(), sources.packName(), relativeDir.isEmpty() ? "root" : relativeDir, e.getMessage(),
				e instanceof RuntimeException ? e : null));
		return retained.isEmpty() ? NO_NAMES : Set.copyOf(retained);
	}

	// ---- defines, uniforms, samplers -----------------------------------------------------------------------------

	/** Every contributor's defines, Visus's own DH defines first (at the position Oculus wrote them). */
	public static void addContributorDefines(List<StringPair> defines) {
		ContributorBridge.defines(registry().contributors(), defines);
	}

	/** API users' uniforms, at the head of {@code CommonUniforms.addNonDynamicUniforms}. */
	public static void addContributorUniforms(UniformHolder holder) {
		UniformContributor[] contributors = registry().contributors();
		if (contributors.length > 1) ContributorBridge.uniforms(contributors, holder);
		else if (contributors.length == 1 && !(contributors[0] instanceof DhDefines)) ContributorBridge.uniforms(contributors, holder);
	}

	/** API users' samplers, at the end of {@code IrisSamplers.addRenderTargetSamplers}. */
	public static void addContributorSamplers(SamplerHolder samplers) {
		UniformContributor[] contributors = registry().contributors();
		if (contributors.length > 1 || (contributors.length == 1 && !(contributors[0] instanceof DhDefines))) {
			ContributorBridge.samplers(contributors, samplers);
		}
	}

	/** Pack programs leave the API's texture units free (wraps the reserved set given to {@code ProgramSamplers}). */
	public static Set<Integer> withReservedUnits(Set<Integer> reserved) {
		Set<Integer> api = VisusApiImpl.INSTANCE.samplerUnitsImpl().reservedTextureUnits();
		if (api.isEmpty()) return reserved;
		int max = SamplerLimits.get().getMaxTextureUnits();
		Set<Integer> merged = new HashSet<>(reserved);
		for (int unit : api) {
			if (unit < max) merged.add(unit);
		}
		if (!checkedUnitLimits) {
			checkedUnitLimits = true;
			for (int unit : api) {
				if (unit >= max) Visus.LOGGER.error("[api] Texture unit {} was allocated, but this GPU has only {} units; it cannot be used", unit, max);
			}
		}
		return merged;
	}

	// ---- custom uniform retention (IrisRenderingPipeline constructor) -------------------------------------------

	private static final class RetentionHolder implements LocationalUniformHolder {
		private final Set<String> names;

		RetentionHolder(Set<String> names) {
			this.names = names;
		}

		@Override
		public LocationalUniformHolder addUniform(UniformUpdateFrequency updateFrequency, Uniform uniform) {
			return this;
		}

		@Override
		public OptionalInt location(String name, UniformType type) {
			return names.contains(name) ? OptionalInt.of(0) : OptionalInt.empty();
		}

		@Override
		public UniformHolder externallyManagedUniform(String name, UniformType type) {
			return this;
		}
	}

	/**
	 * Before {@code customUniforms.optimise()}: marks the uniforms providers declared as used, so the optimiser keeps
	 * them. Returns the marker to pass to {@link #endRetention}, or null.
	 */
	public static Object beginRetention(CustomUniforms customUniforms, Set<String> retained) {
		if (retained == null || retained.isEmpty()) return null;
		RetentionHolder holder = new RetentionHolder(retained);
		customUniforms.assignTo(holder);
		return holder;
	}

	/** After {@code optimise()}: removes the marker, so Iris never pushes values to it. */
	public static void endRetention(CustomUniforms customUniforms, Object marker) {
		if (marker != null) customUniforms.visus$removeHolder(marker);
	}

	// ---- lifecycle ------------------------------------------------------------------------------------------------

	private static PipelineViewImpl view(IrisRenderingPipeline pipeline) {
		synchronized (VIEWS) {
			return VIEWS.computeIfAbsent(pipeline, p -> new PipelineViewImpl(p, Iris.getCurrentPackName()));
		}
	}

	/** After {@code PipelineManager} created and installed a pipeline (never inside its constructor). */
	public static void onPipelineCreated(WorldRenderingPipeline pipeline) {
		if (!(pipeline instanceof IrisRenderingPipeline iris)) return;
		LodRegistry registry = registry();
		if (registry.listeners().length == 0) return;
		PipelineViewImpl view = view(iris);
		registry.forEachListener(l -> l.onPipelineCreated(view), LodHooks::listenerFailed);
	}

	/** At the start of {@code IrisRenderingPipeline.beginLevelRendering}. */
	public static void onBeginLevel(IrisRenderingPipeline pipeline) {
		LodPipelineListener[] listeners = registry().listeners();
		if (listeners.length == 0) return;
		FRAME_VIEWPORT[0] = GlStateManager.Viewport.x();
		FRAME_VIEWPORT[1] = GlStateManager.Viewport.y();
		FRAME_VIEWPORT[2] = GlStateManager.Viewport.width();
		FRAME_VIEWPORT[3] = GlStateManager.Viewport.height();
		PipelineViewImpl view = view(pipeline);
		for (LodPipelineListener listener : listeners) {
			try {
				listener.onBeginLevel(view, FRAME);
			} catch (RuntimeException e) {
				listenerFailed(listener, e);
			}
		}
	}

	/** In {@code PipelineManager.destroyPipeline}, before each pipeline is destroyed. */
	public static void onPipelineDestroyed(WorldRenderingPipeline pipeline) {
		if (!(pipeline instanceof IrisRenderingPipeline iris)) return;
		PipelineViewImpl view;
		synchronized (VIEWS) {
			view = VIEWS.remove(iris);
		}
		if (view == null) {
			if (registry().listeners().length == 0) return;
			view = new PipelineViewImpl(iris, Iris.getCurrentPackName());
		}
		PipelineViewImpl finalView = view;
		registry().forEachListener(l -> l.onPipelineDestroyed(finalView), LodHooks::listenerFailed);
	}

	/** At the end of {@code Iris.reloadNow}: the API state is current and the pipeline is prepared. */
	public static void onReloaded() {
		boolean inUse = VisusApiImpl.INSTANCE.isShaderPackInUse();
		registry().fireReloaded(inUse, (listener, e) ->
			Visus.LOGGER.error("[api] A reload listener ({}) failed", listener.getClass().getName(), e));
	}

	private static void listenerFailed(Object listener, RuntimeException e) {
		Visus.LOGGER.error("[api] A LOD pipeline listener ({}) failed", listener.getClass().getName(), e);
	}

	/** For a pack loaded while a registration was still coming in. */
	static boolean isPackLoaded() {
		return Iris.getCurrentPack().isPresent();
	}
}
