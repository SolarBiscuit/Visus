package net.irisshaders.iris.pipeline.programs;

import net.minecraft.client.renderer.ShaderInstance;

import java.util.function.Function;

/**
 * A specialized map mapping {@link ShaderKey} to {@link ShaderInstance}.
 * Avoids much of the complexity / overhead of an EnumMap while ultimately
 * fulfilling the same function.
 */
public class ShaderMap {
	private final ShaderInstance[] shaders;

	public ShaderMap(Function<ShaderKey, ShaderInstance> factory) {
		// Visus compile service (architecture §5 L2, ported from RatOptimization): before the programs are built one
		// by one, each waiting for its own link, warm up all of their links in parallel; the real builds then find
		// them in the driver's cache. Also times the build for the summary line.
		dev.visus.compile.ShaderBuild.beginBuild("Visus pipeline");
		try {
			dev.visus.compile.ShaderBuild.warm(ShaderKey.values(), factory::apply);

			ShaderKey[] ids = ShaderKey.values();

			this.shaders = new ShaderInstance[ids.length];

			for (int i = 0; i < ids.length; i++) {
				this.shaders[i] = factory.apply(ids[i]);
			}
		} finally {
			dev.visus.compile.ShaderBuild.endBuild();
		}
	}

	public ShaderInstance getShader(ShaderKey id) {
		return shaders[id.ordinal()];
	}
}
