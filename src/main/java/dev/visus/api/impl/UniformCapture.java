package dev.visus.api.impl;

import dev.visus.api.lod.PackUniform;
import dev.visus.api.lod.UniformKind;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.DynamicLocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.LocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.Uniform;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Collects pack uniforms by name as memory writers, for {@code PipelineView.packUniforms}. Iris registers its built-in
 * uniforms through the typed holder methods (each overridden here to capture the value source instead of creating a
 * GL uniform), and custom uniforms through {@link #location}, after which {@code CustomUniforms} hands back the cached
 * uniform objects. Built once per request, at pipeline setup, never per frame.
 */
final class UniformCapture implements DynamicLocationalUniformHolder {
	interface Writer {
		void write(long address);
	}

	record CapturedUniform(String name, UniformKind kind, Writer writer) implements PackUniform {
		@Override
		public void write(long address) {
			writer.write(address);
		}
	}

	private final Set<String> requested;
	private final Map<String, CapturedUniform> captured = new LinkedHashMap<>();
	private final List<String> customOrder = new ArrayList<>();

	UniformCapture(Set<String> requested) {
		this.requested = Set.copyOf(requested);
	}

	List<PackUniform> found() {
		return List.copyOf(captured.values());
	}

	Set<String> missing() {
		Set<String> missing = new LinkedHashSet<>(requested);
		missing.removeAll(captured.keySet());
		return Set.copyOf(missing);
	}

	private boolean wants(String name) {
		return requested.contains(name) && !captured.containsKey(name);
	}

	private void put(String name, UniformKind kind, Writer writer) {
		if (wants(name)) captured.put(name, new CapturedUniform(name, kind, writer));
	}

	// ---- custom uniforms ------------------------------------------------------------------------------------------

	/** Offers custom uniforms only; a built-in that was already captured keeps its direct writer. */
	@Override
	public OptionalInt location(String name, UniformType type) {
		if (!wants(name)) return OptionalInt.empty();
		customOrder.add(name);
		return OptionalInt.of(customOrder.size() - 1);
	}

	/** Turns the cached custom uniforms {@code CustomUniforms.assignTo} matched to this holder into writers. */
	void addCustom(Iterable<CachedUniform> uniforms) {
		FunctionReturn ret = new FunctionReturn();
		for (CachedUniform uniform : uniforms) {
			String name = uniform.getName();
			UniformType type = Type.convert(uniform.getType());
			switch (type) {
				case INT -> put(name, UniformKind.INT, a -> {
					uniform.writeTo(ret);
					MemoryUtil.memPutInt(a, uniform.getType() == Type.Boolean ? (ret.booleanReturn ? 1 : 0) : ret.intReturn);
				});
				case FLOAT -> put(name, UniformKind.FLOAT, a -> {
					uniform.writeTo(ret);
					MemoryUtil.memPutFloat(a, ret.floatReturn);
				});
				case VEC2 -> put(name, UniformKind.VEC2, a -> {
					uniform.writeTo(ret);
					vec2(a, (Vector2f) ret.objectReturn);
				});
				case VEC3 -> put(name, UniformKind.VEC3, a -> {
					uniform.writeTo(ret);
					vec3(a, (Vector3f) ret.objectReturn);
				});
				case VEC4 -> put(name, UniformKind.VEC4, a -> {
					uniform.writeTo(ret);
					vec4(a, (Vector4f) ret.objectReturn);
				});
				case VEC2I -> put(name, UniformKind.VEC2I, a -> {
					uniform.writeTo(ret);
					Vector2i v = (Vector2i) ret.objectReturn;
					MemoryUtil.memPutInt(a, v.x);
					MemoryUtil.memPutInt(a + 4, v.y);
				});
				case VEC3I -> put(name, UniformKind.VEC3I, a -> {
					uniform.writeTo(ret);
					vec3i(a, (Vector3i) ret.objectReturn);
				});
				case MAT4 -> put(name, UniformKind.MAT4, a -> {
					uniform.writeTo(ret);
					mat4(a, (Matrix4f) ret.objectReturn);
				});
				default -> {
				}
			}
		}
	}

	// ---- built-in uniforms ----------------------------------------------------------------------------------------

	@Override
	public LocationalUniformHolder addUniform(UniformUpdateFrequency updateFrequency, Uniform uniform) {
		return this;
	}

	@Override
	public DynamicLocationalUniformHolder addDynamicUniform(Uniform uniform, ValueUpdateNotifier notifier) {
		return this;
	}

	@Override
	public UniformHolder externallyManagedUniform(String name, UniformType type) {
		return this;
	}

	@Override
	public LocationalUniformHolder uniform1f(UniformUpdateFrequency f, String name, FloatSupplier value) {
		put(name, UniformKind.FLOAT, a -> MemoryUtil.memPutFloat(a, value.getAsFloat()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform1f(UniformUpdateFrequency f, String name, IntSupplier value) {
		put(name, UniformKind.FLOAT, a -> MemoryUtil.memPutFloat(a, (float) value.getAsInt()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform1f(UniformUpdateFrequency f, String name, DoubleSupplier value) {
		put(name, UniformKind.FLOAT, a -> MemoryUtil.memPutFloat(a, (float) value.getAsDouble()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform1i(UniformUpdateFrequency f, String name, IntSupplier value) {
		put(name, UniformKind.INT, a -> MemoryUtil.memPutInt(a, value.getAsInt()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform1b(UniformUpdateFrequency f, String name, BooleanSupplier value) {
		put(name, UniformKind.INT, a -> MemoryUtil.memPutInt(a, value.getAsBoolean() ? 1 : 0));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform2f(UniformUpdateFrequency f, String name, Supplier<Vector2f> value) {
		put(name, UniformKind.VEC2, a -> vec2(a, value.get()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform2i(UniformUpdateFrequency f, String name, Supplier<Vector2i> value) {
		put(name, UniformKind.VEC2I, a -> {
			Vector2i v = value.get();
			MemoryUtil.memPutInt(a, v.x);
			MemoryUtil.memPutInt(a + 4, v.y);
		});
		return this;
	}

	@Override
	public LocationalUniformHolder uniform3f(UniformUpdateFrequency f, String name, Supplier<Vector3f> value) {
		put(name, UniformKind.VEC3, a -> vec3(a, value.get()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform3i(UniformUpdateFrequency f, String name, Supplier<Vector3i> value) {
		put(name, UniformKind.VEC3I, a -> vec3i(a, value.get()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniformTruncated3f(UniformUpdateFrequency f, String name, Supplier<Vector4f> value) {
		put(name, UniformKind.VEC3, a -> {
			Vector4f v = value.get();
			MemoryUtil.memPutFloat(a, v.x);
			MemoryUtil.memPutFloat(a + 4, v.y);
			MemoryUtil.memPutFloat(a + 8, v.z);
		});
		return this;
	}

	@Override
	public LocationalUniformHolder uniform3d(UniformUpdateFrequency f, String name, Supplier<Vector3d> value) {
		put(name, UniformKind.VEC3, a -> {
			Vector3d v = value.get();
			MemoryUtil.memPutFloat(a, (float) v.x);
			MemoryUtil.memPutFloat(a + 4, (float) v.y);
			MemoryUtil.memPutFloat(a + 8, (float) v.z);
		});
		return this;
	}

	@Override
	public LocationalUniformHolder uniform4f(UniformUpdateFrequency f, String name, Supplier<Vector4f> value) {
		put(name, UniformKind.VEC4, a -> vec4(a, value.get()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniform4fArray(UniformUpdateFrequency f, String name, Supplier<float[]> value) {
		put(name, UniformKind.VEC4, a -> floats(a, value.get(), 4));
		return this;
	}

	@Override
	public LocationalUniformHolder uniformMatrix(UniformUpdateFrequency f, String name, Supplier<Matrix4f> value) {
		put(name, UniformKind.MAT4, a -> mat4(a, value.get()));
		return this;
	}

	@Override
	public LocationalUniformHolder uniformMatrixFromArray(UniformUpdateFrequency f, String name, Supplier<float[]> value) {
		put(name, UniformKind.MAT4, a -> floats(a, value.get(), 16));
		return this;
	}

	// ---- dynamic (notifier) variants ------------------------------------------------------------------------------

	@Override
	public DynamicLocationalUniformHolder uniform1f(String name, FloatSupplier value, ValueUpdateNotifier n) {
		uniform1f(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicLocationalUniformHolder uniform1f(String name, IntSupplier value, ValueUpdateNotifier n) {
		uniform1f(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicLocationalUniformHolder uniform1f(String name, DoubleSupplier value, ValueUpdateNotifier n) {
		uniform1f(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicLocationalUniformHolder uniform1i(String name, IntSupplier value, ValueUpdateNotifier n) {
		uniform1i(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicLocationalUniformHolder uniform2f(String name, Supplier<Vector2f> value, ValueUpdateNotifier n) {
		uniform2f(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicLocationalUniformHolder uniform2i(String name, Supplier<Vector2i> value, ValueUpdateNotifier n) {
		uniform2i(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicUniformHolder uniform3f(String name, Supplier<Vector3f> value, ValueUpdateNotifier n) {
		uniform3f(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicUniformHolder uniform4f(String name, Supplier<Vector4f> value, ValueUpdateNotifier n) {
		uniform4f(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicUniformHolder uniform4fArray(String name, Supplier<float[]> value, ValueUpdateNotifier n) {
		uniform4fArray(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	@Override
	public DynamicUniformHolder uniform4i(String name, Supplier<Vector4i> value, ValueUpdateNotifier n) {
		put(name, UniformKind.VEC4I, a -> {
			Vector4i v = value.get();
			MemoryUtil.memPutInt(a, v.x);
			MemoryUtil.memPutInt(a + 4, v.y);
			MemoryUtil.memPutInt(a + 8, v.z);
			MemoryUtil.memPutInt(a + 12, v.w);
		});
		return this;
	}

	@Override
	public DynamicUniformHolder uniformMatrix(String name, Supplier<Matrix4f> value, ValueUpdateNotifier n) {
		uniformMatrix(UniformUpdateFrequency.PER_FRAME, name, value);
		return this;
	}

	// ---- writers --------------------------------------------------------------------------------------------------

	private static void vec2(long a, Vector2f v) {
		MemoryUtil.memPutFloat(a, v.x);
		MemoryUtil.memPutFloat(a + 4, v.y);
	}

	private static void vec3(long a, Vector3f v) {
		MemoryUtil.memPutFloat(a, v.x);
		MemoryUtil.memPutFloat(a + 4, v.y);
		MemoryUtil.memPutFloat(a + 8, v.z);
	}

	private static void vec3i(long a, Vector3i v) {
		MemoryUtil.memPutInt(a, v.x);
		MemoryUtil.memPutInt(a + 4, v.y);
		MemoryUtil.memPutInt(a + 8, v.z);
	}

	private static void vec4(long a, Vector4f v) {
		MemoryUtil.memPutFloat(a, v.x);
		MemoryUtil.memPutFloat(a + 4, v.y);
		MemoryUtil.memPutFloat(a + 8, v.z);
		MemoryUtil.memPutFloat(a + 12, v.w);
	}

	private static void floats(long a, float[] values, int count) {
		for (int i = 0; i < count; i++) MemoryUtil.memPutFloat(a + i * 4L, values[i]);
	}

	/** Column-major, as GLSL reads a {@code mat4}: column 0 (m00..m03) first. */
	private static void mat4(long a, Matrix4f m) {
		MemoryUtil.memPutFloat(a, m.m00());
		MemoryUtil.memPutFloat(a + 4, m.m01());
		MemoryUtil.memPutFloat(a + 8, m.m02());
		MemoryUtil.memPutFloat(a + 12, m.m03());
		MemoryUtil.memPutFloat(a + 16, m.m10());
		MemoryUtil.memPutFloat(a + 20, m.m11());
		MemoryUtil.memPutFloat(a + 24, m.m12());
		MemoryUtil.memPutFloat(a + 28, m.m13());
		MemoryUtil.memPutFloat(a + 32, m.m20());
		MemoryUtil.memPutFloat(a + 36, m.m21());
		MemoryUtil.memPutFloat(a + 40, m.m22());
		MemoryUtil.memPutFloat(a + 44, m.m23());
		MemoryUtil.memPutFloat(a + 48, m.m30());
		MemoryUtil.memPutFloat(a + 52, m.m31());
		MemoryUtil.memPutFloat(a + 56, m.m32());
		MemoryUtil.memPutFloat(a + 60, m.m33());
	}
}
