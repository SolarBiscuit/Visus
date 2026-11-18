package dev.visus.api.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.visus.Visus;
import dev.visus.api.lod.PackSampler;
import dev.visus.api.lod.PackSamplers;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.TextureType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Collects pack samplers by name, for {@code PipelineView.packSamplers}: Iris's gbuffer sampler setup is run against
 * this holder instead of a GL program. A name added twice keeps the last texture, as a GL program would (the later
 * {@code glUniform1i} wins). Built once per request, at pipeline setup.
 */
final class SamplerCapture implements SamplerHolder, ImageHolder {
	record CapturedSampler(String name, int glTarget, IntSupplier textureSource, int samplerObject) implements PackSampler {
		@Override
		public int texture() {
			return textureSource.getAsInt();
		}
	}

	private final Set<String> requested;
	private final Map<String, CapturedSampler> captured = new LinkedHashMap<>();

	SamplerCapture(Set<String> requested) {
		this.requested = Set.copyOf(requested);
	}

	private String firstRequested(String... names) {
		for (String name : names) {
			if (requested.contains(name)) return name;
		}
		return null;
	}

	@Override
	public boolean hasSampler(String name) {
		return requested.contains(name);
	}

	@Override
	public void addExternalSampler(int textureUnit, String... names) {
		String name = firstRequested(names);
		if (name == null) return;
		// Vanilla keeps these textures on fixed units (2 is the lightmap); read what it last set there.
		captured.put(name, new CapturedSampler(name, TextureType.TEXTURE_2D.getGlType(),
			() -> RenderSystem.getShaderTexture(textureUnit), 0));
	}

	@Override
	public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
		return addDynamicSampler(type, texture, notifier, sampler, names);
	}

	@Override
	public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
		return addDynamicSampler(type, texture, null, sampler, names);
	}

	@Override
	public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
		String name = firstRequested(names);
		if (name == null) return false;
		captured.put(name, new CapturedSampler(name, type.getGlType(), texture, sampler != null ? sampler.getId() : 0));
		return true;
	}

	@Override
	public boolean hasImage(String name) {
		return false;
	}

	@Override
	public void addTextureImage(IntSupplier textureID, InternalTextureFormat internalFormat, String name) {
	}

	PackSamplers result() {
		CapturedSampler[] samplers = captured.values().toArray(new CapturedSampler[0]);
		Set<String> missing = new LinkedHashSet<>(requested);
		missing.removeAll(captured.keySet());
		return new Result(samplers, Set.copyOf(missing));
	}

	private static final class Result implements PackSamplers {
		private final CapturedSampler[] samplers;
		private final List<PackSampler> found;
		private final Set<String> missing;
		private boolean warnedLimit;

		Result(CapturedSampler[] samplers, Set<String> missing) {
			this.samplers = samplers;
			this.found = List.of(samplers);
			this.missing = missing;
		}

		@Override
		public List<PackSampler> found() {
			return found;
		}

		@Override
		public Set<String> missing() {
			return missing;
		}

		@Override
		public void bind(int baseUnit) {
			int max = SamplerLimits.get().getMaxTextureUnits();
			for (int i = 0; i < samplers.length; i++) {
				int unit = baseUnit + i;
				if (unit < 0 || unit >= max) {
					if (!warnedLimit) {
						warnedLimit = true;
						Visus.LOGGER.error("[api] Cannot bind pack sampler {} to texture unit {}: this GPU has {} units", samplers[i].name(), unit, max);
					}
					continue;
				}
				CapturedSampler s = samplers[i];
				// Through Iris's helpers, so GlStateManager's texture cache and Iris's sampler cache stay correct.
				IrisRenderSystem.bindTextureToUnit(s.glTarget(), unit, s.texture());
				IrisRenderSystem.bindSamplerToUnit(unit, s.samplerObject());
			}
		}
	}
}
