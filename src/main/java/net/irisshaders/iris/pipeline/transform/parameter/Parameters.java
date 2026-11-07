package net.irisshaders.iris.pipeline.transform.parameter;

import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

public abstract class Parameters implements JobParameters {
	public final Patch patch;
	private final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap;
	public PatchShaderType type; // may only be set by TransformPatcher
	// Visus: the two global flags the transform reads, captured when the parameters are made (right before the cache
	// lookup and the transform), so a result cached before either changed is not reused.
	private final boolean separateAo;
	private final boolean debugOptions;
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public Parameters(Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		this.patch = patch;
		this.textureMap = textureMap;
		this.separateAo = WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
		IrisConfig config = Iris.getIrisConfig();
		this.debugOptions = config != null && config.areDebugOptionsEnabled();
	}

	public AlphaTest getAlphaTest() {
		return AlphaTest.ALWAYS;
	}

	public abstract TextureStage getTextureStage();

	public Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
		return textureMap;
	}

	/**
	 * Visus (compile service, ported from RatOptimization's transform_cache): the cache key leaves out {@code type}.
	 * TransformPatcher stores each result under a key whose {@code type} the transform itself has just set, while every
	 * lookup is made with {@code type} still null, so the transform cache never hit. Upstream Iris also leaves it out.
	 * Subclasses still add their own fields through {@code super.hashCode()}/{@code equals()}.
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((patch == null) ? 0 : patch.hashCode());
		result = prime * result + ((textureMap == null) ? 0 : textureMap.hashCode());
		result = prime * result + Boolean.hashCode(separateAo);
		result = prime * result + Boolean.hashCode(debugOptions);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Parameters other = (Parameters) obj;
		if (patch != other.patch)
			return false;
		if (separateAo != other.separateAo || debugOptions != other.debugOptions)
			return false;
		if (textureMap == null) {
			return other.textureMap == null;
		} else return textureMap.equals(other.textureMap);
	}
}
