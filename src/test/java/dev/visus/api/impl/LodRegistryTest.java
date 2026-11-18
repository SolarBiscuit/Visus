package dev.visus.api.impl;

import dev.visus.api.lod.LodPackException;
import dev.visus.api.lod.LodPipelineListener;
import dev.visus.api.lod.LodProgramProvider;
import dev.visus.api.lod.PackSources;
import dev.visus.api.lod.PipelineView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V-13: providers and listeners are isolated from each other's failures; names and colour targets combine. */
class LodRegistryTest {
	private static LodProgramProvider provider(String id, List<String> names, int extraTargets, Runnable onBuilt) {
		return new LodProgramProvider() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public Collection<String> extraSourceNames() {
				return names;
			}

			@Override
			public int extraColorTargets() {
				return extraTargets;
			}

			@Override
			public void onProgramSetBuilt(PackSources sources) throws LodPackException {
				onBuilt.run();
			}
		};
	}

	private static final PackSources SOURCES = new PackSources() {
		@Override
		public String packName() {
			return "test";
		}

		@Override
		public String directory() {
			return "";
		}

		@Override
		public Optional<String> read(String relativePath) {
			return Optional.empty();
		}

		@Override
		public void retainUniforms(Collection<String> names) {
		}
	};

	@Test
	void aThrowingProviderDisablesOnlyItself() {
		LodRegistry registry = new LodRegistry();
		List<String> ran = new ArrayList<>();
		registry.register(provider("bad", List.of(), 0, () -> {
			throw new IllegalStateException("bad voxy.json");
		}));
		registry.register(provider("good", List.of(), 0, () -> ran.add("good")));

		List<String> reported = new ArrayList<>();
		List<String> failed = registry.onProgramSetBuilt(SOURCES, (p, e) -> reported.add(p.id() + ": " + e.getMessage()));

		assertEquals(List.of("bad"), failed);
		assertEquals(List.of("bad: bad voxy.json"), reported);
		assertEquals(List.of("good"), ran, "the provider after the failing one still runs");
	}

	@Test
	void checkedPackExceptionIsReportedNotThrown() {
		LodRegistry registry = new LodRegistry();
		registry.register(new LodProgramProvider() {
			@Override
			public String id() {
				return "voxy";
			}

			@Override
			public Collection<String> extraSourceNames() {
				return List.of("voxy.json");
			}

			@Override
			public void onProgramSetBuilt(PackSources sources) throws LodPackException {
				throw new LodPackException("version must be 1");
			}
		});
		List<String> failed = registry.onProgramSetBuilt(SOURCES, (p, e) -> assertTrue(e instanceof LodPackException));
		assertEquals(List.of("voxy"), failed);
	}

	@Test
	void extraNamesAreMergedInOrderWithoutDuplicates() {
		LodRegistry registry = new LodRegistry();
		registry.register(provider("a", List.of("voxy.json", "voxy_opaque.glsl"), 4, () -> {
		}));
		registry.register(provider("b", List.of("voxy.json", "", "lod.json"), 2, () -> {
		}));
		assertEquals(List.of("voxy.json", "voxy_opaque.glsl", "lod.json"), registry.extraSourceNames());
		assertEquals(4, registry.extraColorTargets(), "the largest request wins");
	}

	@Test
	void extraColorTargetsAreClamped() {
		LodRegistry registry = new LodRegistry();
		registry.register(provider("x", List.of(), 1000, () -> {
		}));
		assertEquals(184, registry.extraColorTargets(), "colortex199 at most");
		LodRegistry negative = new LodRegistry();
		negative.register(provider("y", List.of(), -5, () -> {
		}));
		assertEquals(0, negative.extraColorTargets());
	}

	@Test
	void providerVersionChangesOnRegistration() {
		LodRegistry registry = new LodRegistry();
		int before = registry.providerVersion();
		registry.register(provider("a", List.of("a.json"), 0, () -> {
		}));
		assertTrue(registry.providerVersion() != before);
	}

	@Test
	void aThrowingListenerDoesNotStopTheOthers() {
		LodRegistry registry = new LodRegistry();
		List<String> calls = new ArrayList<>();
		registry.addListener(new LodPipelineListener() {
			@Override
			public void onPipelineCreated(PipelineView pipeline) {
				throw new IllegalStateException("boom");
			}

			@Override
			public void onPipelineDestroyed(PipelineView pipeline) {
			}
		});
		registry.addListener(new LodPipelineListener() {
			@Override
			public void onPipelineCreated(PipelineView pipeline) {
				calls.add("second");
			}

			@Override
			public void onPipelineDestroyed(PipelineView pipeline) {
			}
		});
		List<String> failures = new ArrayList<>();
		registry.forEachListener(l -> l.onPipelineCreated(null), (l, e) -> failures.add(e.getMessage()));
		assertEquals(List.of("second"), calls);
		assertEquals(List.of("boom"), failures);
	}

	@Test
	void reloadListenersRunInRegistrationOrderAndSurviveFailures() {
		LodRegistry registry = new LodRegistry();
		List<String> order = new ArrayList<>();
		registry.addReloadListener(inUse -> order.add("first " + inUse));
		registry.addReloadListener(inUse -> {
			throw new IllegalStateException("x");
		});
		registry.addReloadListener(inUse -> order.add("third " + inUse));
		registry.fireReloaded(true, (l, e) -> order.add("failed"));
		assertEquals(List.of("first true", "failed", "third true"), order);
	}
}
