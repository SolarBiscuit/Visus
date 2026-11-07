package dev.visus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CoO [oculus] absorption: defaults, one-time migration, and the file winning afterwards. */
class VisusConfigTest {
	static final String COO = """
		[general]
		\tskipSignTextInShadowPass = true
		[oculus]
		\t#Skip sign text while rendering the shadow map.
		\tskipSignTextInShadowPass = false
		\tskipGlintInShadowPass = true
		\tskipNameTagsInShadowPass = false # trailing comment
		\tskipBannerPatternsInShadowPass = true
		\tskipBatchingWithoutShaders = false

		[lootr]
		\tskipGlintInShadowPass = false
		""";

	@Test
	void defaultsWhenNothingExists(@TempDir Path dir) throws Exception {
		VisusConfig c = VisusConfig.load(dir.resolve("visus.properties"), dir.resolve("coo.toml"));

		assertEquals("defaults", c.source);
		assertTrue(c.skipSignTextInShadow && c.skipGlintInShadow && c.skipNameTagsInShadow && c.skipBannerPatternsInShadow
			&& c.skipBatchingWithoutShaders);
		assertTrue(Files.exists(dir.resolve("visus.properties")));
	}

	@Test
	void migratesCooOnceThenTheFileWins(@TempDir Path dir) throws Exception {
		Path coo = dir.resolve("coo.toml");
		Files.writeString(coo, COO, StandardCharsets.UTF_8);
		Path file = dir.resolve("visus.properties");

		VisusConfig first = VisusConfig.load(file, coo);
		assertEquals("migrated from CoO", first.source);
		assertFalse(first.skipSignTextInShadow, "[oculus] wins over the same key in another section");
		assertTrue(first.skipGlintInShadow, "[lootr] is ignored");
		assertFalse(first.skipNameTagsInShadow, "a trailing comment is stripped");
		assertTrue(first.skipBannerPatternsInShadow);
		assertFalse(first.skipBatchingWithoutShaders);

		// Changing CoO afterwards has no effect: visus.properties is now the source.
		Files.writeString(coo, COO.replace("skipSignTextInShadowPass = false", "skipSignTextInShadowPass = true"), StandardCharsets.UTF_8);
		VisusConfig second = VisusConfig.load(file, coo);
		assertEquals("file", second.source);
		assertFalse(second.skipSignTextInShadow);
	}

	static final String RATOPT = """
		[modules]
		oculus_shaders = true
		[oculus_shaders]
		## Only used when modules.oculus_shaders = true.
		transform_cache = true
		parallel_link = false
		parallel_contexts = true
		compile_contexts = 12
		audit_mixins = true
		link_log = true
		""";

	@Test
	void upgradesAn024FileWithRatOptCompileSettings(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("visus.properties");
		// What 0.2.4 wrote: shadow and batching keys only, with a user change.
		Files.writeString(file, "shadow.skipSignText=false\nshadow.skipGlint=true\nshadow.skipNameTags=true\n"
			+ "shadow.skipBannerPatterns=true\nbatching.skipWithoutShaders=true\n", StandardCharsets.UTF_8);
		Path ratopt = dir.resolve("ratoptimization.toml");
		Files.writeString(ratopt, RATOPT, StandardCharsets.UTF_8);

		VisusConfig c = VisusConfig.load(file, null, ratopt);
		assertEquals("migrated from RatOptimization", c.source);
		assertFalse(c.skipSignTextInShadow, "the user's existing value is kept");
		assertFalse(c.compileParallelLink);
		assertEquals(VisusConfig.Mode.ON, c.compileParallelContexts, "RatOpt's 'true' means on");
		assertEquals(8, c.compileContexts, "clamped to 1..8");
		assertTrue(c.compileLinkLog);

		VisusConfig again = VisusConfig.load(file, null, ratopt);
		assertEquals("file", again.source, "every key is in the file now");
		assertEquals(VisusConfig.Mode.ON, again.compileParallelContexts);
	}

	@Test
	void compileDefaultsWithoutRatOpt(@TempDir Path dir) throws Exception {
		VisusConfig c = VisusConfig.load(dir.resolve("visus.properties"), null, null);

		assertTrue(c.compileParallelLink);
		assertEquals(VisusConfig.Mode.AUTO, c.compileParallelContexts);
		assertEquals(4, c.compileContexts);
		assertTrue(c.compileAuditMixins);
		assertFalse(c.compileLinkLog);
	}

	@Test
	void badValuesFallBackToDefaults(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("visus.properties");
		Files.writeString(file, "shadow.skipGlint=maybe\nshadow.skipNameTags=FALSE\n", StandardCharsets.UTF_8);

		VisusConfig c = VisusConfig.load(file, null);
		assertTrue(c.skipGlintInShadow);
		assertFalse(c.skipNameTagsInShadow);
	}
}
