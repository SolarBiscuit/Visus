package dev.visus.api.impl;

import dev.visus.Visus;
import dev.visus.api.ReloadListener;
import dev.visus.api.VisusApi;
import dev.visus.api.gl.GlApi;
import dev.visus.api.gl.GlScope;
import dev.visus.api.lod.LodApi;
import dev.visus.api.lod.LodPipelineListener;
import dev.visus.api.lod.LodProgramProvider;
import dev.visus.api.lod.SamplerUnits;
import dev.visus.api.lod.UniformContributor;
import dev.visus.api.spi.ApiInstance;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.LoadingModList;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The Visus implementation of the public API (V-13, architecture §4.5). Installed into {@link ApiInstance} when this
 * class initialises, which {@link Visus}'s constructor forces; Oculus's own sources reach it through {@link LodHooks}.
 */
public final class VisusApiImpl implements VisusApi, LodApi, GlApi {
	public static final VisusApiImpl INSTANCE = new VisusApiImpl();

	static {
		ApiInstance.install(INSTANCE);
	}

	private final LodRegistry registry = new LodRegistry();
	private final SamplerUnitsImpl samplerUnits = new SamplerUnitsImpl();
	private final GlScopeImpl scope = new GlScopeImpl();
	private final ReloadRequests reloads = new ReloadRequests(
		task -> Minecraft.getInstance().tell(task),
		VisusApiImpl::reloadBetweenFrames);

	private VisusApiImpl() {
		// Distant Horizons' defines go through the API like any user's, and first, where Oculus wrote them.
		registry.register(new DhDefines(() ->
			LoadingModList.get().getModFileById("distanthorizons") != null && DHCompat.hasRenderingEnabled()));
	}

	/** Forces installation; called from the Visus mod constructor, before other mods are constructed after it. */
	public static void init() {
		Visus.LOGGER.info("Visus API revision {} installed", REVISION);
	}

	LodRegistry registry() {
		return registry;
	}

	SamplerUnitsImpl samplerUnitsImpl() {
		return samplerUnits;
	}

	// ---- VisusApi -------------------------------------------------------------------------------------------------

	@Override
	public int revision() {
		return REVISION;
	}

	@Override
	public boolean isShaderPackInUse() {
		return IrisApi.getInstance().isShaderPackInUse();
	}

	@Override
	public boolean isRenderingShadowPass() {
		return IrisApi.getInstance().isRenderingShadowPass();
	}

	@Override
	public Optional<String> currentPackName() {
		if (Iris.getIrisConfig() == null || !Iris.getIrisConfig().areShadersEnabled()) return Optional.empty();
		return Optional.ofNullable(Iris.getCurrentPackName());
	}

	@Override
	public Path shaderpacksDirectory() {
		return Iris.getShaderpacksDirectory();
	}

	@Override
	public void requestReload(String reason) {
		if (reloads.request(reason)) {
			Visus.LOGGER.info("[api] Shader reload requested ({}); it runs at the next frame boundary", reason);
		}
	}

	private static void reloadBetweenFrames() {
		try {
			Iris.reload();
		} catch (Exception e) {
			Visus.LOGGER.error("[api] Requested shader reload failed", e);
		}
	}

	@Override
	public void addReloadListener(ReloadListener listener) {
		registry.addReloadListener(listener);
	}

	@Override
	public LodApi lod() {
		return this;
	}

	@Override
	public GlApi gl() {
		return this;
	}

	// ---- LodApi ---------------------------------------------------------------------------------------------------

	@Override
	public void register(LodProgramProvider provider) {
		registry.register(provider);
		Visus.LOGGER.info("[api] LOD program provider '{}' registered (sources {}, {} extra colour targets)",
			provider.id(), provider.extraSourceNames(), provider.extraColorTargets());
		reloadIfAlreadyLoaded("LOD provider " + provider.id());
	}

	@Override
	public void addListener(LodPipelineListener listener) {
		registry.addListener(listener);
	}

	@Override
	public void register(UniformContributor contributor) {
		registry.register(contributor);
		reloadIfAlreadyLoaded("uniform contributor " + contributor.getClass().getSimpleName());
	}

	@Override
	public SamplerUnits samplerUnits() {
		return new SamplerUnits() {
			@Override
			public int allocateTextureUnits(String owner, int count) {
				int before = samplerUnits.version();
				int first = samplerUnits.allocateTextureUnits(owner, count);
				if (samplerUnits.version() != before) {
					Visus.LOGGER.info("[api] Texture units {}..{} reserved for {}", first, first + count - 1, owner);
					reloadIfAlreadyLoaded("texture units for " + owner);
				}
				return first;
			}

			@Override
			public int allocateStorageBindings(String owner, int count) {
				int before = samplerUnits.version();
				int first = samplerUnits.allocateStorageBindings(owner, count);
				if (samplerUnits.version() != before) {
					Visus.LOGGER.info("[api] Storage buffer bindings {}..{} reserved for {}", first, first + count - 1, owner);
				}
				return first;
			}
		};
	}

	/** A registration after a pack has loaded only takes effect on the next load, so ask for one. */
	private void reloadIfAlreadyLoaded(String what) {
		if (Minecraft.getInstance() != null && LodHooks.isPackLoaded()) {
			requestReload(what + " registered after the pack loaded");
		}
	}

	// ---- GlApi ----------------------------------------------------------------------------------------------------

	@Override
	public GlScope openScope(String owner) {
		return scope.open(owner);
	}
}
