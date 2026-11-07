// Visus ABI census and check. Dependency-free (JDK 17+), run as a single-file program:
//   java tools/abi/AbiCensus.java census <modsDir> <out.txt> [excludeSubstring...]
//   java tools/abi/AbiCensus.java check  <abi-freeze.txt> <visus.jar> [report.txt]
//
// census: scans every jar (and nested jars) in modsDir for references into the frozen
// packages (Oculus/Iris and the libraries it bundles) and records them, one per line:
//   CLASS   <owner>                                   a class reference
//   FIELD   <owner>#<name>:<desc>                     a field reference
//   METHOD  <owner>#<name><desc>                      a method reference
//   MIXIN   <target>                                  @Mixin target
//   INJECT  <target>#<selector>                       method selector in @Inject/@Redirect/@ModifyArg/...
//   (an @At(target = ...) naming a frozen member is recorded as a METHOD or FIELD entry; since 2026-11-06)
//   SHADOW  <target>#<name>                           @Shadow / @Accessor / @Invoker member
//   REFLECT <string>                                  string constant naming a frozen package
// Each line ends with "\t<jar>" listing the dependents (comma separated).
// check: verifies each entry still exists in the given jar (REFLECT lines are reported, not enforced).
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class AbiCensus {
    static final String[] FROZEN = {"net/irisshaders/", "net/coderbot/", "kroppeb/stareval/", "io/github/douira/glsl_transformer/", "oculus/org/"};
    static final String[] FROZEN_DOT = {"net.irisshaders.", "net.coderbot."};

    static boolean frozen(String internal) {
        if (internal == null) return false;
        String s = internal;
        while (s.startsWith("[")) s = s.substring(1);
        if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length() - 1);
        for (String p : FROZEN) if (s.startsWith(p)) return true;
        return false;
    }

    // ---------------------------------------------------------------- class file model
    static final class ClassInfo {
        String name, superName;
        final List<String> interfaces = new ArrayList<>();
        final Set<String> fields = new HashSet<>();   // name:desc
        final Set<String> methods = new HashSet<>();  // name+desc
        final Set<String> classRefs = new TreeSet<>();
        final Set<String> fieldRefs = new TreeSet<>(), methodRefs = new TreeSet<>();
        final Set<String> strings = new TreeSet<>();
        final List<String> mixinTargets = new ArrayList<>();
        final List<String> selectors = new ArrayList<>();   // raw selectors from injector annotations
        final List<String> atTargets = new ArrayList<>();   // @At(target = "Lowner;member...") injection points
        final List<String> shadows = new ArrayList<>();     // member names
    }

    static final class Reader {
        final DataInputStream in;
        Object[] cp;
        byte[] tags;
        Reader(byte[] b) { in = new DataInputStream(new ByteArrayInputStream(b)); }
        String utf(int i) { return (String) cp[i]; }
        String cls(int i) { return utf((Integer) cp[i]); }
    }

    static ClassInfo parse(byte[] bytes) throws IOException {
        Reader r = new Reader(bytes);
        DataInputStream in = r.in;
        if (in.readInt() != 0xCAFEBABE) return null;
        in.readUnsignedShort(); in.readUnsignedShort();
        int n = in.readUnsignedShort();
        r.cp = new Object[n];
        r.tags = new byte[n];
        int[][] refs = new int[n][];
        for (int i = 1; i < n; i++) {
            int tag = in.readUnsignedByte();
            r.tags[i] = (byte) tag;
            switch (tag) {
                case 1 -> r.cp[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 7, 8, 16, 19, 20 -> r.cp[i] = in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> refs[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                default -> throw new IOException("bad cp tag " + tag);
            }
        }
        ClassInfo ci = new ClassInfo();
        for (int i = 1; i < n; i++) {
            int t = r.tags[i];
            if (t == 7) ci.classRefs.add(r.cls(i));
            else if (t == 8) ci.strings.add(r.utf((Integer) r.cp[i]));
            else if (t == 9 || t == 10 || t == 11) {
                String owner = r.cls(refs[i][0]);
                int[] nt = refs[refs[i][1]];
                String name = r.utf(nt[0]), desc = r.utf(nt[1]);
                if (t == 9) ci.fieldRefs.add(owner + "#" + name + ":" + desc);
                else ci.methodRefs.add(owner + "#" + name + desc);
            } else if (t == 1) {
                // descriptors mention classes that may never appear as CONSTANT_Class
                String s = r.utf(i);
                int idx = 0;
                while ((idx = s.indexOf('L', idx)) >= 0) {
                    int end = s.indexOf(';', idx);
                    if (end < 0) break;
                    String c = s.substring(idx + 1, end);
                    if (frozen(c) && c.indexOf(' ') < 0) ci.classRefs.add(c);
                    idx = end;
                }
            }
        }
        in.readUnsignedShort();
        ci.name = r.cls(in.readUnsignedShort());
        int sup = in.readUnsignedShort();
        ci.superName = sup == 0 ? null : r.cls(sup);
        int ic = in.readUnsignedShort();
        for (int i = 0; i < ic; i++) ci.interfaces.add(r.cls(in.readUnsignedShort()));
        for (int kind = 0; kind < 2; kind++) {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                in.readUnsignedShort();
                String name = r.utf(in.readUnsignedShort()), desc = r.utf(in.readUnsignedShort());
                if (kind == 0) ci.fields.add(name + ":" + desc); else ci.methods.add(name + desc);
                readAttributes(r, ci, name);
            }
        }
        readAttributes(r, ci, null);
        return ci;
    }

    static void readAttributes(Reader r, ClassInfo ci, String memberName) throws IOException {
        int ac = r.in.readUnsignedShort();
        for (int a = 0; a < ac; a++) {
            String an = r.utf(r.in.readUnsignedShort());
            int len = r.in.readInt();
            if (an.equals("RuntimeInvisibleAnnotations") || an.equals("RuntimeVisibleAnnotations")) {
                int na = r.in.readUnsignedShort();
                for (int i = 0; i < na; i++) annotation(r, ci, memberName);
            } else r.in.skipNBytes(len);
        }
    }

    static void annotation(Reader r, ClassInfo ci, String memberName) throws IOException {
        String type = r.utf(r.in.readUnsignedShort());
        int pairs = r.in.readUnsignedShort();
        boolean isMixin = type.equals("Lorg/spongepowered/asm/mixin/Mixin;");
        boolean isShadowLike = type.equals("Lorg/spongepowered/asm/mixin/Shadow;")
                || type.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;")
                || type.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;");
        boolean isInjector = type.startsWith("Lorg/spongepowered/asm/mixin/injection/") || type.startsWith("Lcom/llamalad7/mixinextras/injector/");
        boolean explicitShadowName = false;
        for (int p = 0; p < pairs; p++) {
            String key = r.utf(r.in.readUnsignedShort());
            List<Object> vals = new ArrayList<>();
            elementValue(r, ci, vals, memberName);
            if (isMixin && (key.equals("value") || key.equals("targets"))) {
                for (Object v : vals) {
                    String s = v.toString();
                    if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length() - 1);
                    ci.mixinTargets.add(s.replace('.', '/'));
                }
            } else if (type.equals("Lorg/spongepowered/asm/mixin/injection/At;") && key.equals("target")) {
                for (Object v : vals) ci.atTargets.add(v.toString());
            } else if (isInjector && key.equals("method")) {
                for (Object v : vals) ci.selectors.add(v.toString());
            } else if (isShadowLike && key.equals("value")) {
                for (Object v : vals) if (!v.toString().isEmpty()) { ci.shadows.add(v.toString()); explicitShadowName = true; }
            }
        }
        if (isShadowLike && !explicitShadowName && memberName != null) {
            String n = memberName;
            if (type.endsWith("Accessor;") || type.endsWith("Invoker;")) {
                for (String pre : new String[]{"get", "set", "is", "call", "invoke"})
                    if (n.startsWith(pre) && n.length() > pre.length()) { n = Character.toLowerCase(n.charAt(pre.length())) + n.substring(pre.length() + 1); break; }
            }
            ci.shadows.add(n);
        }
    }

    static void elementValue(Reader r, ClassInfo ci, List<Object> out, String memberName) throws IOException {
        int tag = r.in.readUnsignedByte();
        switch (tag) {
            case 's' -> out.add(r.utf(r.in.readUnsignedShort()));
            case 'c' -> out.add(r.utf(r.in.readUnsignedShort()));
            case 'e' -> { r.in.readUnsignedShort(); r.in.readUnsignedShort(); }
            case '@' -> annotation(r, ci, memberName);
            case '[' -> { int n = r.in.readUnsignedShort(); for (int i = 0; i < n; i++) elementValue(r, ci, out, memberName); }
            default -> r.in.readUnsignedShort();
        }
    }

    // ---------------------------------------------------------------- jar walking
    interface ClassSink { void accept(String jarLabel, ClassInfo ci); }

    static void walkJar(String label, InputStream raw, ClassSink sink) throws IOException {
        ZipInputStream zin = new ZipInputStream(raw);
        ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            String n = e.getName();
            if (n.endsWith(".class") && !n.startsWith("META-INF/versions/")) {
                try {
                    ClassInfo ci = parse(zin.readAllBytes());
                    if (ci != null) sink.accept(label, ci);
                } catch (Exception ex) {
                    System.err.println("skip " + label + "!" + n + ": " + ex);
                }
            } else if (n.endsWith(".jar")) {
                walkJar(label + "!" + n.substring(n.lastIndexOf('/') + 1), new ByteArrayInputStream(zin.readAllBytes()), sink);
            }
        }
    }

    // ---------------------------------------------------------------- census
    static void census(Path modsDir, Path out, List<String> excludes) throws IOException {
        Map<String, Set<String>> entries = new TreeMap<>();
        List<Path> jars;
        try (var s = Files.list(modsDir)) {
            jars = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        }
        int scanned = 0;
        for (Path jar : jars) {
            String file = jar.getFileName().toString();
            if (excludes.stream().anyMatch(file::contains)) { System.err.println("excluded " + file); continue; }
            scanned++;
            try (InputStream in = Files.newInputStream(jar)) {
                walkJar(file, in, (label, ci) -> {
                    if (frozen(ci.name)) return; // a jar that bundles Iris classes itself (e.g. a fork) is not a dependent
                    String dep = label.contains("!") ? label.substring(0, label.indexOf('!')) : label;
                    for (String c : ci.classRefs) if (frozen(c) && !c.startsWith("[")) add(entries, "CLASS\t" + c, dep);
                    for (String f : ci.fieldRefs) if (frozen(f.substring(0, f.indexOf('#')))) add(entries, "FIELD\t" + f, dep);
                    for (String m : ci.methodRefs) if (frozen(m.substring(0, m.indexOf('#')))) add(entries, "METHOD\t" + m, dep);
                    for (String s : ci.strings) for (String p : FROZEN_DOT) if (s.contains(p)) add(entries, "REFLECT\t" + s, dep);
                    // An injection point naming a frozen member (an INVOKE or FIELD target) depends on that member too.
                    for (String at : ci.atTargets) {
                        if (!at.startsWith("L") || at.indexOf(';') < 0) continue;
                        String owner = at.substring(1, at.indexOf(';'));
                        String member = at.substring(at.indexOf(';') + 1);
                        if (!frozen(owner) || member.isEmpty()) continue;
                        int colon = member.indexOf(':');
                        if (colon >= 0) add(entries, "FIELD	" + owner + "#" + member.substring(0, colon) + ":" + member.substring(colon + 1), dep);
                        else if (member.contains("(")) add(entries, "METHOD	" + owner + "#" + member, dep);
                    }
                    for (String t : ci.mixinTargets) {
                        if (!frozen(t)) continue;
                        add(entries, "MIXIN\t" + t, dep);
                        for (String sel : ci.selectors) add(entries, "INJECT\t" + t + "#" + sel, dep);
                        for (String sh : ci.shadows) add(entries, "SHADOW\t" + t + "#" + sh, dep);
                    }
                });
            }
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("# Visus ABI freeze list. Generated by tools/abi/AbiCensus.java census on " + new Date());
            w.println("# Source: " + modsDir + " (" + scanned + " jars scanned; excluded: " + excludes + ")");
            w.println("# Remove an entry only with a dated note in abi/RETIRED.md naming the dependent that moved off it.");
            for (var en : entries.entrySet()) w.println(en.getKey() + "\t" + String.join(",", en.getValue()));
        }
        System.out.println("census: " + entries.size() + " entries from " + scanned + " jars -> " + out);
    }

    static void add(Map<String, Set<String>> m, String k, String dep) { m.computeIfAbsent(k, x -> new TreeSet<>()).add(dep); }

    // ---------------------------------------------------------------- check
    static Map<String, ClassInfo> index(Path jar) throws IOException {
        Map<String, ClassInfo> map = new HashMap<>();
        try (InputStream in = Files.newInputStream(jar)) {
            walkJar(jar.getFileName().toString(), in, (l, ci) -> map.putIfAbsent(ci.name, ci));
        }
        return map;
    }

    static boolean hasMember(Map<String, ClassInfo> idx, String owner, String member, boolean field) {
        Deque<String> todo = new ArrayDeque<>(List.of(owner));
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String c = todo.pop();
            if (!seen.add(c)) continue;
            ClassInfo ci = idx.get(c);
            if (ci == null) return !frozen(c); // inherited from a non-frozen (Minecraft/JDK) type: assume present
            if (field ? ci.fields.contains(member) : ci.methods.contains(member)) return true;
            if (ci.superName != null) todo.add(ci.superName);
            todo.addAll(ci.interfaces);
        }
        return false;
    }

    static boolean hasMemberNamed(ClassInfo ci, String sel) {
        // selectors: "name", "name(desc)", "name*", "Lowner;name(desc)", "<init>", regex-ish forms are accepted loosely
        String s = sel;
        int semi = s.indexOf(';');
        if (s.startsWith("L") && semi > 0) s = s.substring(semi + 1);
        String name = s, desc = null;
        int paren = s.indexOf('(');
        if (paren >= 0) { name = s.substring(0, paren); desc = s.substring(paren); }
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(0, colon);
        if (name.endsWith("*")) name = name.substring(0, name.length() - 1);
        for (String m : ci.methods) {
            if (!m.startsWith(name + "(")) continue;
            if (desc == null || m.equals(name + desc)) return true;
        }
        for (String f : ci.fields) if (f.startsWith(name + ":")) return true;
        return false;
    }

    static boolean overridesVanilla(Map<String, ClassInfo> idx, ClassInfo ci) {
        for (ClassInfo c = ci; c != null; c = c.superName == null ? null : idx.get(c.superName)) {
            if (c.superName != null && !frozen(c.superName) && !c.superName.equals("java/lang/Object")) return true;
            for (String i : c.interfaces) if (!frozen(i)) return true;
        }
        return false;
    }

    static int check(Path freeze, Path jar, Path report) throws IOException {
        Map<String, ClassInfo> idx = index(jar);
        List<String> missing = new ArrayList<>(), unverified = new ArrayList<>();
        int checked = 0, reflect = 0;
        for (String line : Files.readAllLines(freeze)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t");
            String kind = p[0], key = p[1];
            boolean ok;
            switch (kind) {
                case "CLASS", "MIXIN" -> ok = idx.containsKey(key);
                case "FIELD" -> { int h = key.indexOf('#'); ok = hasMember(idx, key.substring(0, h), key.substring(h + 1), true); }
                case "METHOD" -> { int h = key.indexOf('#'); ok = hasMember(idx, key.substring(0, h), key.substring(h + 1), false); }
                case "INJECT", "SHADOW" -> {
                    int h = key.indexOf('#');
                    ClassInfo ci = idx.get(key.substring(0, h));
                    ok = ci != null && hasMemberNamed(ci, key.substring(h + 1));
                    if (!ok && ci != null && overridesVanilla(idx, ci)) {
                        // dev-named selector of an inherited Minecraft member (resolved via refmap at runtime)
                        unverified.add(line);
                        continue;
                    }
                }
                case "REFLECT" -> { reflect++; continue; }
                default -> { continue; }
            }
            checked++;
            if (!ok) missing.add(line);
        }
        String summary = "abiCheck: " + checked + " entries checked, " + missing.size() + " missing, "
                + unverified.size() + " unverifiable (inherited vanilla names), " + reflect + " reflective strings (not enforced)";
        try (PrintWriter w = new PrintWriter(report == null ? new OutputStreamWriter(System.out) : Files.newBufferedWriter(report))) {
            w.println(summary);
            for (String m : missing) w.println("MISSING\t" + m);
            for (String m : unverified) w.println("UNVERIFIED\t" + m);
        }
        System.out.println(summary);
        return missing.isEmpty() ? 0 : 1;
    }

    public static void main(String[] args) throws IOException {
        if (args.length >= 3 && args[0].equals("census")) {
            census(Path.of(args[1]), Path.of(args[2]), Arrays.asList(args).subList(3, args.length));
        } else if (args.length >= 3 && args[0].equals("check")) {
            System.exit(check(Path.of(args[1]), Path.of(args[2]), args.length > 3 ? Path.of(args[3]) : null));
        } else {
            System.err.println("usage: census <modsDir> <out> [exclude...] | check <freeze> <jar> [report]");
            System.exit(2);
        }
    }
}
