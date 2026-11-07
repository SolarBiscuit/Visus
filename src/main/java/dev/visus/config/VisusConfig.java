package dev.visus.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Visus's own settings, in {@code config/visus.properties}. Oculus's settings stay in {@code oculus.properties}.
 *
 * <p>Some settings come from mods whose job Visus has built in (architecture §4.4): the shadow-pass skips and the
 * batching skip from Collections of Optimizations' {@code [oculus]} section (0.2.4), and the compile service from
 * RatOptimization's {@code [oculus_shaders]} section (0.3.1). A key missing from this file (a new file, or one written
 * by an older Visus) is taken once from that mod's config if present, otherwise from the default, and the file is
 * rewritten with every key. After that this file is the only source.
 *
 * <p>Values are read once at startup into public fields (read on hot render paths, so plain fields).
 */
public final class VisusConfig {
	public static final String FILE_NAME = "visus.properties";
	public static final String COO_FILE_NAME = "collections_of_optimizations-common.toml";
	public static final String RATOPT_FILE_NAME = "ratoptimization.toml";
	/** Per-driver verdicts of the compile service's self-checks (the driver quirks file). */
	public static final String DRIVER_FILE_NAME = "visus-driver.properties";
	public static final String RATOPT_DRIVER_FILE_NAME = "ratoptimization-oculus-driver.properties";

	/** How a parallel compile path is chosen: always, never, or where the other path cannot run. */
	public enum Mode {
		OFF, ON, AUTO;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Mode parse(String v) {
			if (v == null) {
				return null;
			}
			return switch (v.trim().toLowerCase(Locale.ROOT)) {
				case "on", "true" -> ON;
				case "off", "false" -> OFF;
				case "auto" -> AUTO;
				default -> null;
			};
		}
	}

	private enum Source { COO, RATOPT }

	private enum Kind { BOOL, MODE, CONTEXTS }

	private record Key(String name, Source source, String section, String sourceKey, String def, Kind kind) {
	}

	private static final List<Key> KEYS = List.of(
		new Key("shadow.skipSignText", Source.COO, "oculus", "skipSignTextInShadowPass", "true", Kind.BOOL),
		new Key("shadow.skipGlint", Source.COO, "oculus", "skipGlintInShadowPass", "true", Kind.BOOL),
		new Key("shadow.skipNameTags", Source.COO, "oculus", "skipNameTagsInShadowPass", "true", Kind.BOOL),
		new Key("shadow.skipBannerPatterns", Source.COO, "oculus", "skipBannerPatternsInShadowPass", "true", Kind.BOOL),
		new Key("batching.skipWithoutShaders", Source.COO, "oculus", "skipBatchingWithoutShaders", "true", Kind.BOOL),
		new Key("compile.parallelLink", Source.RATOPT, "oculus_shaders", "parallel_link", "true", Kind.BOOL),
		new Key("compile.parallelContexts", Source.RATOPT, "oculus_shaders", "parallel_contexts", "auto", Kind.MODE),
		new Key("compile.contexts", Source.RATOPT, "oculus_shaders", "compile_contexts", "4", Kind.CONTEXTS),
		new Key("compile.auditMixins", Source.RATOPT, "oculus_shaders", "audit_mixins", "true", Kind.BOOL),
		new Key("compile.linkLog", Source.RATOPT, "oculus_shaders", "link_log", "false", Kind.BOOL)
	);

	private static volatile VisusConfig instance = new VisusConfig(defaults(), "defaults");
	private static volatile Path configDir = Path.of("config");

	/** Skip sign text in the shadow pass. */
	public final boolean skipSignTextInShadow;
	/** Report items as having no enchantment glint in the shadow pass. */
	public final boolean skipGlintInShadow;
	/** Skip entity name tags in the shadow pass. */
	public final boolean skipNameTagsInShadow;
	/** Skip banner pattern layers in the shadow pass, keeping the base cloth. */
	public final boolean skipBannerPatternsInShadow;
	/** Use the vanilla entity buffer source while no shader pack is in use. */
	public final boolean skipBatchingWithoutShaders;
	/** Compile service: warm up pipeline links on the driver's own compiler threads (KHR/ARB_parallel_shader_compile). */
	public final boolean compileParallelLink;
	/** Compile service: compile and link copies on hidden shared GL contexts. */
	public final Mode compileParallelContexts;
	/** Compile service: how many hidden compiler contexts (1 to 8). */
	public final int compileContexts;
	/** Compile service: switch the warm-ups off if other mods patch the classes they run twice. */
	public final boolean compileAuditMixins;
	/** Compile service: log every program link with its time. */
	public final boolean compileLinkLog;
	/** Where the values came from, for the log. */
	public final String source;

	private VisusConfig(Properties p, String source) {
		this.skipSignTextInShadow = bool(p, "shadow.skipSignText");
		this.skipGlintInShadow = bool(p, "shadow.skipGlint");
		this.skipNameTagsInShadow = bool(p, "shadow.skipNameTags");
		this.skipBannerPatternsInShadow = bool(p, "shadow.skipBannerPatterns");
		this.skipBatchingWithoutShaders = bool(p, "batching.skipWithoutShaders");
		this.compileParallelLink = bool(p, "compile.parallelLink");
		this.compileParallelContexts = Mode.parse(p.getProperty("compile.parallelContexts"));
		this.compileContexts = Integer.parseInt(p.getProperty("compile.contexts"));
		this.compileAuditMixins = bool(p, "compile.auditMixins");
		this.compileLinkLog = bool(p, "compile.linkLog");
		this.source = source;
	}

	public static VisusConfig get() {
		return instance;
	}

	/** The config directory given to {@link #init}. */
	public static Path configDir() {
		return configDir;
	}

	/** Loads (or creates) the file in {@code dir}, makes it current, and seeds the driver file from RatOptimization's. */
	public static VisusConfig init(Path dir) throws IOException {
		configDir = dir;
		VisusConfig c = load(dir.resolve(FILE_NAME), dir.resolve(COO_FILE_NAME), dir.resolve(RATOPT_FILE_NAME));
		Path driver = dir.resolve(DRIVER_FILE_NAME);
		Path ratoptDriver = dir.resolve(RATOPT_DRIVER_FILE_NAME);
		if (!Files.exists(driver) && Files.exists(ratoptDriver)) {
			Files.copy(ratoptDriver, driver);
		}
		instance = c;
		return c;
	}

	public static VisusConfig load(Path file, Path cooFile) throws IOException {
		return load(file, cooFile, null);
	}

	/**
	 * Reads {@code file}. Keys it does not have are taken from CoO's or RatOptimization's config (if that file exists)
	 * or the defaults, and then the file is rewritten with every key.
	 */
	public static VisusConfig load(Path file, Path cooFile, Path ratOptFile) throws IOException {
		Properties p = defaults();
		Set<String> present = new LinkedHashSet<>();
		if (Files.exists(file)) {
			Properties read = new Properties();
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				read.load(r);
			}
			for (Key k : KEYS) {
				String v = read.getProperty(k.name());
				if (v != null) {
					present.add(k.name());
					String value = normalise(k, v);
					if (value != null) {
						p.setProperty(k.name(), value);
					}
				}
			}
		}
		if (present.size() == KEYS.size()) {
			return new VisusConfig(p, "file");
		}

		Map<Source, Map<String, String>> sections = new HashMap<>();
		sections.put(Source.COO, readSection(cooFile, "oculus"));
		sections.put(Source.RATOPT, readSection(ratOptFile, "oculus_shaders"));
		List<String> migrated = new ArrayList<>();
		for (Key k : KEYS) {
			if (present.contains(k.name())) {
				continue;
			}
			String value = normalise(k, sections.get(k.source()).get(k.sourceKey()));
			if (value != null) {
				p.setProperty(k.name(), value);
				String from = k.source() == Source.COO ? "CoO" : "RatOptimization";
				if (!migrated.contains(from)) {
					migrated.add(from);
				}
			}
		}
		String source = migrated.isEmpty() ? (present.isEmpty() ? "defaults" : "file, new keys from defaults")
			: "migrated from " + String.join(" and ", migrated);
		write(file, p, source);
		return new VisusConfig(p, source);
	}

	private static Map<String, String> readSection(Path toml, String section) throws IOException {
		if (toml == null || !Files.exists(toml)) {
			return Map.of();
		}
		return readTomlSection(Files.readAllLines(toml, StandardCharsets.UTF_8), section);
	}

	private static String normalise(Key k, String v) {
		if (v == null) {
			return null;
		}
		String t = v.trim().toLowerCase(Locale.ROOT);
		return switch (k.kind()) {
			case BOOL -> t.equals("true") || t.equals("false") ? t : null;
			case MODE -> {
				Mode m = Mode.parse(t);
				yield m == null ? null : m.id();
			}
			case CONTEXTS -> {
				try {
					yield String.valueOf(Math.max(1, Math.min(8, Integer.parseInt(t))));
				} catch (NumberFormatException e) {
					yield null;
				}
			}
		};
	}

	private static void write(Path file, Properties p, String source) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			w.write("# Visus settings (Oculus's own settings stay in oculus.properties).\n");
			w.write("# Last written from: " + source + ".\n");
			w.write("#\n");
			w.write("# Shadow pass: skip sign text, enchantment glint, name tags and banner pattern layers. They are rarely\n");
			w.write("# visible in shadows and cost draw calls.\n");
			w.write("# batching.skipWithoutShaders: use the vanilla entity buffers while no shader pack is in use.\n");
			w.write("#\n");
			w.write("# Compile service (pack load and apply):\n");
			w.write("#   compile.parallelLink: warm up the pipeline's program links on the driver's compiler threads.\n");
			w.write("#   compile.parallelContexts: auto | on | off. Compile copies on hidden shared GL contexts; auto uses them\n");
			w.write("#     only where parallelLink cannot run (for example AMD on Windows).\n");
			w.write("#   compile.contexts: how many hidden contexts (1 to 8).\n");
			w.write("#   compile.auditMixins: turn the warm-ups off if other mods patch the shader build classes they run twice.\n");
			w.write("#   compile.linkLog: log every program link with its time.\n");
			w.write("#   Each path checks itself per driver; verdicts are kept in " + DRIVER_FILE_NAME + " (delete it to test again).\n");
			for (Key k : KEYS) {
				w.write(k.name() + "=" + p.getProperty(k.name()) + "\n");
			}
		}
	}

	/**
	 * Reads {@code key = value} lines from one {@code [section]} of a flat TOML file. Enough for CoO's and
	 * RatOptimization's configs: no nested tables, no multi-line values.
	 */
	static Map<String, String> readTomlSection(List<String> lines, String section) {
		Map<String, String> out = new HashMap<>();
		boolean in = false;
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("[")) {
				in = line.equals("[" + section + "]");
				continue;
			}
			int eq = line.indexOf('=');
			if (in && eq > 0) {
				String value = line.substring(eq + 1).trim();
				int hash = value.indexOf('#');
				if (hash >= 0) {
					value = value.substring(0, hash).trim();
				}
				if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length() - 1);
				}
				out.put(line.substring(0, eq).trim(), value);
			}
		}
		return out;
	}

	private static Properties defaults() {
		Properties p = new Properties();
		for (Key k : KEYS) {
			p.setProperty(k.name(), k.def());
		}
		return p;
	}

	private static boolean bool(Properties p, String key) {
		return Boolean.parseBoolean(p.getProperty(key));
	}
}
