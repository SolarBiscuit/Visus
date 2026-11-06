package dev.visus.util;

/**
 * File-name helpers (bug ledger V-16).
 */
public final class FileNames {
	private FileNames() {
	}

	/**
	 * Makes a program name safe to use as one file name inside one folder. Path separators and characters that Windows
	 * forbids become {@code _}. Names that are already safe, such as every shader pack program name, are returned
	 * unchanged, so T1 dump file names do not change.
	 */
	public static String sanitize(String name) {
		StringBuilder sb = null;

		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			boolean bad = c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<'
				|| c == '>' || c == '|' || c < 0x20;

			if (bad) {
				if (sb == null) {
					sb = new StringBuilder(name.length());
					sb.append(name, 0, i);
				}

				sb.append('_');
			} else if (sb != null) {
				sb.append(c);
			}
		}

		return sb == null ? name : sb.toString();
	}
}
