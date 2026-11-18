package dev.visus.api.impl;

import dev.visus.Visus;
import dev.visus.api.lod.DefineSink;
import dev.visus.api.lod.FloatArraySource;
import dev.visus.api.lod.UniformContributor;
import dev.visus.api.lod.UniformSink;
import dev.visus.api.lod.UpdateFrequency;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.helpers.StringPair;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Feeds {@link UniformContributor}s into Iris's define list, uniform holders and sampler holders. */
final class ContributorBridge {
	private ContributorBridge() {
	}

	/** Appends every contributor's defines, as {@code StandardMacros} writes its own ({@code key, ""} for a flag). */
	static void defines(UniformContributor[] contributors, List<StringPair> out) {
		DefineSink sink = new DefineSink() {
			@Override
			public void define(String name) {
				out.add(new StringPair(name, ""));
			}

			@Override
			public void define(String name, String value) {
				out.add(new StringPair(name, value));
			}
		};
		for (UniformContributor contributor : contributors) {
			try {
				contributor.defines(sink);
			} catch (RuntimeException e) {
				Visus.LOGGER.error("[api] A uniform contributor ({}) failed while adding defines", contributor.getClass().getName(), e);
			}
		}
	}

	/** Adds every contributor's uniforms to a holder (called while Iris builds its common uniforms). */
	static void uniforms(UniformContributor[] contributors, UniformHolder holder) {
		UniformSink sink = new UniformSink() {
			@Override
			public void mat4(String name, UpdateFrequency frequency, FloatArraySource value) {
				float[] scratch = new float[16];
				Matrix4f matrix = new Matrix4f();
				holder.uniformMatrix(map(frequency), name, () -> {
					value.write(scratch);
					return matrix.set(scratch);
				});
			}

			@Override
			public void vec3(String name, UpdateFrequency frequency, FloatArraySource value) {
				float[] scratch = new float[3];
				Vector3f vector = new Vector3f();
				holder.uniform3f(map(frequency), name, () -> {
					value.write(scratch);
					return vector.set(scratch[0], scratch[1], scratch[2]);
				});
			}

			@Override
			public void float1(String name, UpdateFrequency frequency, DoubleSupplier value) {
				holder.uniform1f(map(frequency), name, value);
			}

			@Override
			public void int1(String name, UpdateFrequency frequency, IntSupplier value) {
				holder.uniform1i(map(frequency), name, value);
			}

			@Override
			public void sampler(String name, IntSupplier glTexture) {
				// Samplers are added separately, from IrisSamplers#addRenderTargetSamplers.
			}
		};
		for (UniformContributor contributor : contributors) {
			try {
				contributor.uniforms(sink);
			} catch (RuntimeException e) {
				Visus.LOGGER.error("[api] A uniform contributor ({}) failed while adding uniforms", contributor.getClass().getName(), e);
			}
		}
	}

	/** Adds every contributor's samplers to a program's sampler holder (at the end, as Oculus adds the DH samplers). */
	static void samplers(UniformContributor[] contributors, SamplerHolder samplers) {
		UniformSink sink = new UniformSink() {
			@Override
			public void mat4(String name, UpdateFrequency frequency, FloatArraySource value) {
			}

			@Override
			public void vec3(String name, UpdateFrequency frequency, FloatArraySource value) {
			}

			@Override
			public void float1(String name, UpdateFrequency frequency, DoubleSupplier value) {
			}

			@Override
			public void int1(String name, UpdateFrequency frequency, IntSupplier value) {
			}

			@Override
			public void sampler(String name, IntSupplier glTexture) {
				samplers.addDynamicSampler(glTexture, name);
			}
		};
		for (UniformContributor contributor : contributors) {
			try {
				contributor.uniforms(sink);
			} catch (RuntimeException e) {
				Visus.LOGGER.error("[api] A uniform contributor ({}) failed while adding samplers", contributor.getClass().getName(), e);
			}
		}
	}

	private static UniformUpdateFrequency map(UpdateFrequency frequency) {
		return switch (frequency) {
			case ONCE -> UniformUpdateFrequency.ONCE;
			case PER_TICK -> UniformUpdateFrequency.PER_TICK;
			case PER_FRAME -> UniformUpdateFrequency.PER_FRAME;
		};
	}
}
