package dev.visus.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Visus's own settings, in {@code config/visus.properties}. Oculus's settings stay in {@code oculus.properties}.
 *
 * <p>The shadow-pass skips and the batching skip come from Collections of Optimizations' {@code [oculus]} section
 * (architecture §4.4), which Visus has built in since 0.2.4. The first time the file is created, the user's values from
 * {@code collections_of_optimizations-common.toml} are copied over; after that this file is the only source.
 *
 * <p>Values are read once at startup into public fields (read on hot render paths, so plain fields).
 */
public final class VisusConfig {
	public static final String FILE_NAME = "visus.properties";
	public static final String COO_FILE_NAME = "collections_of_optimizations-common.toml";

	/** Visus key, CoO {@code [oculus]} key, default. */
	private static final List<String[]> KEYS = List.of(
		new String[]{"shadow.skipSignText", "skipSignTextInShadowPass", "true"},
		new String[]{"shadow.skipGlint", "skipGlintInShadowPass", "true"},
		new String[]{"shadow.skipNameTags", "skipNameTagsInShadowPass", "true"},
		new String[]{"shadow.skipBannerPatterns", "skipBannerPatternsInShadowPass", "true"},
		new String[]{"batching.skipWithoutShaders", "skipBatchingWithoutShaders", "true"}
	);

	private static volatile VisusConfig instance = new VisusConfig(defaults(), "defaults");

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
	/** Where the values came from: "file", "migrated from CoO", or "defaults". */
	public final String source;

	private VisusConfig(Properties p, String source) {
		this.skipSignTextInShadow = bool(p, "shadow.skipSignText");
		this.skipGlintInShadow = bool(p, "shadow.skipGlint");
		this.skipNameTagsInShadow = bool(p, "shadow.skipNameTags");
		this.skipBannerPatternsInShadow = bool(p, "shadow.skipBannerPatterns");
		this.skipBatchingWithoutShaders = bool(p, "batching.skipWithoutShaders");
		this.source = source;
	}

	public static VisusConfig get() {
		return instance;
	}

	/** Loads (or creates) the file in {@code configDir} and makes it current. */
	public static VisusConfig init(Path configDir) throws IOException {
		VisusConfig c = load(configDir.resolve(FILE_NAME), configDir.resolve(COO_FILE_NAME));
		instance = c;
		return c;
	}

	/**
	 * Reads {@code file}. If it does not exist, takes the defaults overridden by CoO's {@code [oculus]} values (if that
	 * file exists) and writes {@code file}.
	 */
	public static VisusConfig load(Path file, Path cooFile) throws IOException {
		Properties p = defaults();
		if (Files.exists(file)) {
			Properties read = new Properties();
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				read.load(r);
			}
			for (String[] k : KEYS) {
				String v = read.getProperty(k[0]);
				if (isBool(v)) {
					p.setProperty(k[0], v.trim().toLowerCase());
				}
			}
			return new VisusConfig(p, "file");
		}

		String source = "defaults";
		if (cooFile != null && Files.exists(cooFile)) {
			Map<String, String> coo = readTomlSection(Files.readAllLines(cooFile, StandardCharsets.UTF_8), "oculus");
			for (String[] k : KEYS) {
				String v = coo.get(k[1]);
				if (isBool(v)) {
					p.setProperty(k[0], v.trim().toLowerCase());
					source = "migrated from CoO";
				}
			}
		}
		write(file, p, source);
		return new VisusConfig(p, source);
	}

	private static void write(Path file, Properties p, String source) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			w.write("# Visus settings (Oculus's own settings stay in oculus.properties).\n");
			w.write("# Created from " + source + ".\n");
			w.write("# Shadow pass: skip sign text, enchantment glint, name tags and banner pattern layers. They are rarely\n");
			w.write("# visible in shadows and cost draw calls.\n");
			w.write("# batching.skipWithoutShaders: use the vanilla entity buffers while no shader pack is in use.\n");
			for (String[] k : KEYS) {
				w.write(k[0] + "=" + p.getProperty(k[0]) + "\n");
			}
		}
	}

	/**
	 * Reads {@code key = value} lines from one {@code [section]} of a flat TOML file. Enough for CoO's config: no
	 * nested tables, no multi-line values.
	 */
	static Map<String, String> readTomlSection(List<String> lines, String section) {
		Map<String, String> out = new java.util.HashMap<>();
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
				out.put(line.substring(0, eq).trim(), value);
			}
		}
		return out;
	}

	private static Properties defaults() {
		Properties p = new Properties();
		for (String[] k : KEYS) {
			p.setProperty(k[0], k[2]);
		}
		return p;
	}

	private static boolean isBool(String v) {
		return v != null && (v.trim().equalsIgnoreCase("true") || v.trim().equalsIgnoreCase("false"));
	}

	private static boolean bool(Properties p, String key) {
		return Boolean.parseBoolean(p.getProperty(key));
	}
}
