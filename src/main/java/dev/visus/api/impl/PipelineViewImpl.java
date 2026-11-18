package dev.visus.api.impl;

import dev.visus.api.lod.PackSamplers;
import dev.visus.api.lod.PackUniforms;
import dev.visus.api.lod.PipelineView;
import dev.visus.api.lod.RenderTargetInfo;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/** {@link PipelineView} over one {@link IrisRenderingPipeline}. One instance per pipeline, so it can be a map key. */
public final class PipelineViewImpl implements PipelineView {
	private final IrisRenderingPipeline pipeline;
	private final String packName;

	PipelineViewImpl(IrisRenderingPipeline pipeline, String packName) {
		this.pipeline = pipeline;
		this.packName = packName == null ? "" : packName;
	}

	@Override
	public String packName() {
		return packName;
	}

	@Override
	public String directory() {
		return pipeline.visus$directory();
	}

	@Override
	public RenderTargetInfo colorTarget(int index) {
		int count = pipeline.visus$renderTargets().getRenderTargetCount();
		if (index < 0 || index >= count) {
			throw new IllegalArgumentException("colortex" + index + " is outside colortex0..colortex" + (count - 1));
		}
		RenderTarget target = pipeline.visus$renderTargets().getOrCreate(index);
		return new RenderTargetInfo(index, target.getMainTexture(), target.getAltTexture(),
			target.getInternalFormat().getGlFormat(), target.getWidth(), target.getHeight(),
			pipeline.getFlippedAfterPrepare().contains(index));
	}

	@Override
	public boolean separateAo() {
		return WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
	}

	@Override
	public int blockStateId(Object blockState) {
		Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
		if (ids == null || !(blockState instanceof BlockState state)) return -1;
		return ids.getOrDefault(state, -1);
	}

	@Override
	public PackUniforms packUniforms(Set<String> names) {
		UniformCapture capture = new UniformCapture(names);
		// Built-ins first (they get direct writers), as the pipeline builds its own common uniforms.
		CommonUniforms.addNonDynamicUniforms(capture, pipeline.visus$pack().getIdMap(), pipeline.visus$packDirectives(),
			pipeline.visus$updateNotifier());
		CommonUniforms.addDynamicUniforms(capture, FogMode.PER_FRAGMENT);
		// Then custom uniforms. The holder is taken straight back out, so Iris never pushes to its made-up locations.
		CustomUniforms custom = pipeline.visus$customUniforms();
		custom.assignTo(capture);
		Object2IntMap<CachedUniform> matched = custom.visus$removeHolder(capture);
		if (matched != null) capture.addCustom(matched.keySet());

		List<dev.visus.api.lod.PackUniform> found = capture.found();
		Set<String> missing = capture.missing();
		return new PackUniforms() {
			@Override
			public List<dev.visus.api.lod.PackUniform> found() {
				return found;
			}

			@Override
			public Set<String> missing() {
				return missing;
			}
		};
	}

	@Override
	public PackSamplers packSamplers(Set<String> names) {
		SamplerCapture capture = new SamplerCapture(names);
		pipeline.addGbufferOrShadowSamplers(capture, capture, pipeline::getFlippedAfterPrepare, false, true, true, false);
		return capture.result();
	}

	@Override
	public int storageBuffer(int index) {
		ShaderStorageBufferHolder holder = pipeline.visus$shaderStorageBuffers();
		if (holder == null || index < 0) return 0;
		try {
			return holder.getBufferIndex(index);
		} catch (RuntimeException e) {
			return 0;
		}
	}
}
