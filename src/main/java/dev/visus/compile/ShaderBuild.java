// Ported from RatOptimization's oculus_shaders module (MIT, Copyright (c) 2026 SolarBiscuit) into the Visus
// compile service (architecture §4.4, §5 L2). Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.compile;

import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.visus.config.VisusConfig;
import dev.visus.mixin.compile.ProgramIdAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.opengl.ARBParallelShaderCompile;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.KHRParallelShaderCompile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Oculus shader builds: timing summary, optional link log, and the parallel-link warm-up.
 * <p>
 * Warm-up (pipeline programs only): before Oculus builds its gbuffer/shadow programs one by one (each build waits
 * for the driver to finish its link), the same build code runs once per program and is stopped right after
 * glLinkProgram. The driver compiles all of them on its own compiler threads (GL_KHR/ARB_parallel_shader_compile,
 * capped at a few); once they are done the throwaway objects are deleted and Oculus builds normally, finding every
 * program in the driver's cache. Everything stays on the render thread and the one GL context. Nothing is deleted
 * while the driver is still linking it: programs it has not finished are kept until it reports them done.
 * <p>
 * Context warm-up (parallel_contexts, takes precedence when on): the same build code runs once per program and is
 * stopped right before glLinkProgram. The program's final stage sources and attribute bindings are copied to
 * CompileContexts, whose workers compile and link their own copies on their own GL contexts at the same time.
 * Nothing is linked on Minecraft's context during this warm-up, so the half-built objects are deleted right away.
 * <p>
 * The terrain programs are only timed (see OculusChunkProgramOverridesMixin).
 */
public final class ShaderBuild {

    private static final Logger LOGGER = LoggerFactory.getLogger("Visus");

    private static final String DRIVER_FILE = VisusConfig.DRIVER_FILE_NAME;
    /** Prefix of the driver file key for parallel_contexts verdicts. */
    private static final String CONTEXTS_KEY = "contexts | ";
    private static final int GL_COMPLETION_STATUS = 0x91B1; // same value for KHR and ARB
    private static final long WAIT_LIMIT_NANOS = TimeUnit.SECONDS.toNanos(120);
    /** Above this, the real links after a warm-up were not served from the driver's cache. */
    private static final double MAX_WARM_LINK_MS = 25.0;
    /** Above this per program, the driver compiled while the links were issued, so nothing ran in parallel. */
    private static final double MAX_ISSUE_MS = 150.0;

    /** Mixins from these packages may patch the classes the warm-up runs twice. */
    private static final String[] AUDIT_ALLOWED = {
            "dev.visus.",
            "com.solarbiscuit.ratoptimization.", // RatOptimization's other modules
            "net.irisshaders.",          // Oculus itself
            "net.fabricmc.fabric.",      // Forgified Fabric API rendering (shader id handling only)
            "me.jellysquid.mods.sodium.", // Embeddium/Xenon
            "org.embeddedt.embeddium."
    };
    private static final String[] AUDIT_CLASSES = {
            "net/minecraft/client/renderer/ShaderInstance",
            "com/mojang/blaze3d/shaders/ProgramManager",
            "com/mojang/blaze3d/shaders/Program",
            "net/irisshaders/iris/pipeline/programs/ExtendedShader",
            "net/irisshaders/iris/pipeline/programs/FallbackShader",
            "net/irisshaders/iris/pipeline/programs/ShaderMap"
    };

    /** Stackless control-flow exception thrown right after glLinkProgram during a warm-up. */
    public static final class Abort extends RuntimeException {
        private Abort() {
            super("Visus parallel link warm-up", null, false, false);
        }
    }

    public static final Abort ABORT = new Abort();

    /** Builds the object for one key. Returns what was built if the build was not stopped at its link. */
    @FunctionalInterface
    public interface Step<K> {
        Object build(K key) throws Exception;
    }

    private static final class Build {
        final String label;
        final long start = System.nanoTime();
        /** Compiler contexts the warm-up used (parallel_contexts); 0 for the parallel_link warm-up. */
        int warmContexts;
        int warmIssued;
        long warmIssueNanos;
        long warmWaitNanos;
        /** Warm-up links the driver had not finished when the warm-up stopped waiting. */
        int warmPending;
        int links;
        long linkNanos;
        long slowestNanos;
        String slowestName;
        int changed;
        int hashed;

        Build(String label) {
            this.label = label;
        }
    }

    private static boolean warming;
    /** The running warm-up is the parallel_contexts one (stop before the link, copy sources to the contexts). */
    private static boolean capturing;
    private static final IntArrayList warmPrograms = new IntArrayList();
    /** Attribute bindings made on warm-up programs before their link, per program, for the context copies. */
    private static final Map<Integer, List<CompileContexts.Attrib>> warmAttribs = new HashMap<>();
    private static final List<CompletableFuture<Void>> contextJobs = new ArrayList<>();
    /** Half-built pieces of the warm-up batch, each released once: stage programs (GL shaders) and uniforms. */
    private static final Set<Program> warmStages = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Uniform> warmUniforms = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Stage programs that existed before the warm-up (shared by name); never released by it. */
    private static final Set<Program> stagesBefore = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Warm-up programs the driver was still linking when the warm-up stopped waiting, and the stage shaders of that
     * batch. Deleted once the driver reports all of those links done (checked when a build starts and ends).
     */
    private static final IntArrayList pendingPrograms = new IntArrayList();
    private static final IntArrayList pendingShaders = new IntArrayList();

    private static Build build;
    private static long linkStart;

    private static Boolean gate;
    private static Boolean contextsGate;
    private static boolean threadsCapped;
    private static boolean pathLogged;
    private static String driverKey;
    private static final Map<String, Integer> lastSourceHash = new HashMap<>();

    private ShaderBuild() {
    }

    /** Only on the render thread, so a link made on any other thread is never stopped or recorded. */
    public static boolean isWarming() {
        return warming && RenderSystem.isOnRenderThread();
    }

    // ---- build sessions -------------------------------------------------------------------------------------------

    public static void beginBuild(String label) {
        if (RenderSystem.isOnRenderThread()) {
            releasePending();
            build = new Build(label);
        }
    }

    public static void endBuild() {
        Build b = build;
        if (b == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        build = null;
        double totalMs = (System.nanoTime() - b.start) / 1e6;
        double avgLinkMs = b.links == 0 ? 0 : b.linkNanos / 1e6 / b.links;
        StringBuilder line = new StringBuilder("[compile] ").append(b.label).append(": ")
                .append(b.links).append(" programs built in ").append(ms(totalMs));
        if (b.warmIssued > 0 && b.warmContexts > 0) {
            line.append(" (warm-up on ").append(b.warmContexts).append(" compiler contexts: ").append(b.warmIssued)
                    .append(" programs captured in ").append(ms(b.warmIssueNanos / 1e6));
            if (b.warmPending > 0) {
                line.append(", stopped waiting after ").append(ms(b.warmWaitNanos / 1e6)).append(" with ")
                        .append(b.warmPending).append(" still compiling)");
            } else {
                line.append(", compiled ").append(ms(b.warmWaitNanos / 1e6)).append(" later)");
            }
        } else if (b.warmIssued > 0) {
            line.append(" (warm-up: ").append(b.warmIssued).append(" links issued in ").append(ms(b.warmIssueNanos / 1e6));
            if (b.warmPending > 0) {
                line.append(", stopped waiting after ").append(ms(b.warmWaitNanos / 1e6)).append(" with ")
                        .append(b.warmPending).append(" still linking)");
            } else {
                line.append(", driver finished ").append(ms(b.warmWaitNanos / 1e6)).append(" later)");
            }
        }
        line.append("; link wait avg ").append(String.format("%.1f", avgLinkMs)).append(" ms");
        if (b.slowestName != null) {
            line.append(", slowest ").append(b.slowestName).append(' ').append(ms(b.slowestNanos / 1e6));
        }
        if (b.hashed > 0) {
            line.append("; ").append(b.changed).append(" of ").append(b.hashed).append(" program sources changed since the last build");
        }
        LOGGER.info(line.toString());
        // A warm-up that stopped waiting says nothing about this driver's cache; it already switched itself off.
        if (b.warmIssued > 0 && b.links > 0 && b.warmPending == 0) {
            validate(b, avgLinkMs);
        }
        releasePending();
    }

    public static void linkStart() {
        if (build != null && !warming && RenderSystem.isOnRenderThread()) {
            linkStart = System.nanoTime();
        }
    }

    public static void linkEnd(String name, int program) {
        Build b = build;
        if (b == null || warming || linkStart == 0L || !RenderSystem.isOnRenderThread()) {
            return;
        }
        long nanos = System.nanoTime() - linkStart;
        linkStart = 0L;
        b.links++;
        b.linkNanos += nanos;
        if (nanos > b.slowestNanos) {
            b.slowestNanos = nanos;
            b.slowestName = name;
        }
        if (VisusConfig.get().compileLinkLog) {
            int hash = sourceHash(program);
            Integer previous = lastSourceHash.put(b.label + '/' + name, hash);
            b.hashed++;
            if (previous == null || previous != hash) {
                b.changed++;
            }
            LOGGER.info("[compile] link {} {} ms source={}{}", name, String.format("%.2f", nanos / 1e6),
                    Integer.toHexString(hash), previous == null ? " (new)" : previous != hash ? " (changed)" : "");
        }
    }

    private static int sourceHash(int program) {
        try {
            int[] count = new int[1];
            int[] shaders = new int[8];
            GL20C.glGetAttachedShaders(program, count, shaders);
            int hash = 1;
            for (int i = 0; i < count[0]; i++) {
                hash = 31 * hash + GL20C.glGetShaderSource(shaders[i]).hashCode();
            }
            return hash;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ---- warm-up --------------------------------------------------------------------------------------------------

    /**
     * Called at the link site instead of the blocking status query while warming. Throws {@link #ABORT}, on the
     * render thread during a warm-up only.
     */
    public static void linkIssued(int program) {
        if (!isWarming() || capturing) {
            return;
        }
        warmPrograms.add(program);
        throw ABORT;
    }

    /** True while a parallel_contexts warm-up runs on this thread: stop each build before its link. */
    public static boolean isCapturing() {
        return capturing && isWarming();
    }

    /** Records an attribute binding made before the link, for the context copy of a warm-up program. */
    public static void attribBound(int program, int index, CharSequence name) {
        if (isCapturing()) {
            warmAttribs.computeIfAbsent(program, k -> new ArrayList<>())
                    .add(new CompileContexts.Attrib(index, name.toString()));
        }
    }

    /**
     * Called at the link site right before glLinkProgram while capturing: hands a copy of the program's attached stage
     * sources and attribute bindings to the compiler contexts and throws {@link #ABORT}. The program itself is never
     * linked, so it and its stages can be deleted as soon as the warm-up loop ends.
     */
    public static void captureBeforeLink(int program, String name) {
        if (!isCapturing()) {
            return;
        }
        warmPrograms.add(program);
        int[] count = new int[1];
        int[] shaders = new int[8];
        GL20C.glGetAttachedShaders(program, count, shaders);
        List<CompileContexts.Stage> stages = new ArrayList<>(count[0]);
        for (int i = 0; i < count[0]; i++) {
            stages.add(new CompileContexts.Stage(GL20C.glGetShaderi(shaders[i], GL20C.GL_SHADER_TYPE),
                    GL20C.glGetShaderSource(shaders[i])));
        }
        List<CompileContexts.Attrib> attribs = warmAttribs.remove(program);
        contextJobs.add(CompileContexts.submit(new CompileContexts.Job(name, stages,
                attribs != null ? attribs : List.of())));
        throw ABORT;
    }

    /**
     * Remembers a half-built piece (stage program, uniform) to release once the warm-up batch is done. Each object is
     * released once, and stage programs that existed before the warm-up are left alone.
     */
    public static void releaseLater(Program stage) {
        if (stage != null && isWarming() && !stagesBefore.contains(stage)) {
            warmStages.add(stage);
        }
    }

    public static void releaseLater(List<Uniform> uniforms) {
        if (uniforms != null && isWarming()) {
            warmUniforms.addAll(uniforms);
        }
    }

    /**
     * Runs {@code step} for every key with link status queries replaced by {@link #linkIssued} (parallel_link), or with
     * each link replaced by {@link #captureBeforeLink} (parallel_contexts), then waits for the driver or the compiler
     * contexts to finish and deletes the throwaway objects. Any failure other than the abort stops that warm-up for
     * this session; the real build that follows then runs exactly as without RatOptimization.
     */
    public static <K> void warm(K[] keys, Step<K> step) {
        Build b = build;
        if (b == null || warming || !RenderSystem.isOnRenderThread()) {
            return;
        }
        VisusConfig config = VisusConfig.get();
        VisusConfig.Mode mode = config.compileParallelContexts;
        boolean linkOk = false;
        boolean contexts;
        if (mode == VisusConfig.Mode.ON) {
            contexts = contextsGateOpen() && CompileContexts.start(config.compileContexts);
            if (!contexts) {
                linkOk = linkReady(config);
            }
        } else {
            // auto: the compiler contexts only stand in where parallel_link will not run (no extension, off for this
            // driver, or switched off this session), e.g. AMD on Windows, which compiles inside glLinkProgram.
            linkOk = linkReady(config);
            contexts = mode == VisusConfig.Mode.AUTO && !linkOk && contextsGateOpen()
                    && CompileContexts.start(config.compileContexts);
        }
        if (!contexts && !linkOk) {
            return;
        }
        if (!pathLogged) {
            pathLogged = true;
            LOGGER.info("[compile] warm-up uses {} (parallel_contexts = {})",
                    contexts ? "the compiler contexts" : "parallel_link", mode.id());
        }
        long t0 = System.nanoTime();
        for (Program.Type type : Program.Type.values()) {
            stagesBefore.addAll(type.getPrograms().values());
        }
        warming = true;
        capturing = contexts;
        try {
            for (K key : keys) {
                try {
                    if (step.build(key) != null) {
                        // Finished without stopping at the link: the link hook is not active. The object belongs to
                        // its owner as usual (Oculus frees it with the pipeline); only the warm-up stops.
                        disable(contexts, "a warm-up build for " + key + " was not stopped at its link");
                        break;
                    }
                } catch (Throwable t) {
                    if (!isAbort(t)) {
                        disable(contexts, "warm-up failed at " + key + ": " + t);
                        break;
                    }
                }
            }
        } finally {
            warming = false;
            capturing = false;
            warmAttribs.clear();
        }
        long t1 = System.nanoTime();
        b.warmIssued += warmPrograms.size();
        b.warmIssueNanos += t1 - t0;
        if (contexts) {
            b.warmContexts = CompileContexts.count();
            try {
                b.warmPending += awaitContextJobs(t1);
            } finally {
                b.warmWaitNanos += System.nanoTime() - t1;
                // Nothing of the batch was linked on this context.
                releaseBatch(warmPrograms.size());
            }
            return;
        }
        int done = 0;
        try {
            done = awaitLinks(t1);
        } finally {
            b.warmWaitNanos += System.nanoTime() - t1;
            b.warmPending += warmPrograms.size() - done;
            releaseBatch(done);
        }
    }

    /**
     * Waits until the compiler contexts finished every copy of the batch. Returns how many were not done at the time
     * limit; those keep compiling on their workers (which delete them) and parallel_contexts goes off for the session.
     */
    private static int awaitContextJobs(long since) {
        int pending = 0;
        try {
            for (CompletableFuture<Void> job : contextJobs) {
                long left = WAIT_LIMIT_NANOS - (System.nanoTime() - since);
                try {
                    job.get(Math.max(0L, left), TimeUnit.NANOSECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    // A copy that failed in the driver only means that program is not cached; the real build reports it.
                    LOGGER.debug("[compile] context copy failed: {}", e.getCause().toString());
                } catch (java.util.concurrent.TimeoutException e) {
                    pending++;
                }
            }
            if (pending > 0) {
                disable(true, "the compiler contexts were still compiling after "
                        + TimeUnit.NANOSECONDS.toSeconds(WAIT_LIMIT_NANOS) + " s; " + pending + " copies left");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending = Math.max(pending, 1);
            disable(true, "interrupted while waiting for the compiler contexts");
        } finally {
            contextJobs.clear();
        }
        return pending;
    }

    /**
     * Polls GL_COMPLETION_STATUS (which never blocks) until the driver reports every link of the batch done. Returns
     * how many programs, in issue order, it confirmed. Stops early and switches parallel_link off for the session
     * when the driver rejects the query (a failed query reads as "not done" and would be polled to the time limit)
     * or is still compiling at the time limit.
     */
    private static int awaitLinks(long since) {
        int done = 0;
        try {
            clearGlErrors();
            for (; done < warmPrograms.size(); done++) {
                int program = warmPrograms.getInt(done);
                int complete = GL20C.glGetProgrami(program, GL_COMPLETION_STATUS);
                int error = GL11C.glGetError();
                if (error != GL11C.GL_NO_ERROR) {
                    disable("the driver rejected the link completion query (GL error 0x" + Integer.toHexString(error)
                            + "); " + (warmPrograms.size() - done) + " warm-up programs are kept until their links finish");
                    return done;
                }
                while (complete == GL20C.GL_FALSE) {
                    if (System.nanoTime() - since > WAIT_LIMIT_NANOS) {
                        disable("the driver was still compiling after " + TimeUnit.NANOSECONDS.toSeconds(WAIT_LIMIT_NANOS)
                                + " s; " + (warmPrograms.size() - done) + " warm-up programs are kept until it reports them done");
                        return done;
                    }
                    LockSupport.parkNanos(200_000L);
                    complete = GL20C.glGetProgrami(program, GL_COMPLETION_STATUS);
                }
            }
        } catch (Throwable t) {
            disable("waiting for the warm-up links failed: " + t);
        }
        return done;
    }

    /**
     * Deletes what the warm-up created. Programs not confirmed done go to the pending list instead, and so do the
     * GL shaders of every stage of the batch (any of them may be attached to such a program); {@link #releasePending}
     * deletes them once the driver reports those links done. Uniform buffers are plain memory and freed now.
     */
    private static void releaseBatch(int done) {
        boolean keepStages = done < warmPrograms.size();
        for (int i = 0; i < warmPrograms.size(); i++) {
            if (i < done) {
                GL20C.glDeleteProgram(warmPrograms.getInt(i));
            } else {
                pendingPrograms.add(warmPrograms.getInt(i));
            }
        }
        warmPrograms.clear();
        for (Program stage : warmStages) {
            try {
                if (keepStages) {
                    keepUntilLinked(stage);
                } else {
                    stage.close();
                }
            } catch (Throwable ignored) {
            }
        }
        warmStages.clear();
        for (Uniform uniform : warmUniforms) {
            try {
                uniform.close();
            } catch (Throwable ignored) {
            }
        }
        warmUniforms.clear();
        stagesBefore.clear();
    }

    /**
     * Takes a stage out of the shared-by-name stage maps now, so the real build compiles its own as after a normal
     * warm-up, and leaves its GL shader to {@link #releasePending}. Program.close() is not used later because it also
     * drops whatever the real build has filed under the same name.
     */
    private static void keepUntilLinked(Program stage) {
        for (Program.Type type : Program.Type.values()) {
            if (type != null) {
                type.getPrograms().remove(stage.getName(), stage);
            }
        }
        if (stage instanceof ProgramIdAccessor accessor && accessor.visus$id() > 0) {
            pendingShaders.add(accessor.visus$id());
        }
    }

    /**
     * Deletes kept warm-up programs the driver now reports done, and the batch's stage shaders once none is left.
     * Called on the render thread when a build starts and ends.
     */
    private static void releasePending() {
        if (pendingPrograms.isEmpty()) {
            return;
        }
        for (int i = pendingPrograms.size() - 1; i >= 0; i--) {
            int program = pendingPrograms.getInt(i);
            clearGlErrors();
            boolean done = GL20C.glGetProgrami(program, GL_COMPLETION_STATUS) != GL20C.GL_FALSE;
            if (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
                // The driver cannot answer: wait for the link the ordinary way, with the link status query every
                // normal build makes. A build has run since the link was issued, so it has long had its turn.
                GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS);
                done = true;
            }
            if (done) {
                GL20C.glDeleteProgram(program);
                pendingPrograms.removeInt(i);
            }
        }
        if (pendingPrograms.isEmpty()) {
            for (int i = 0; i < pendingShaders.size(); i++) {
                GL20C.glDeleteShader(pendingShaders.getInt(i));
            }
            LOGGER.info("[compile] the driver finished the kept warm-up links; deleted them and {} stage shaders",
                    pendingShaders.size());
            pendingShaders.clear();
        }
    }

    /** Drops GL errors left by earlier calls, so the next glGetError reports only the call made after this. */
    private static void clearGlErrors() {
        for (int i = 0; i < 8 && GL11C.glGetError() != GL11C.GL_NO_ERROR; i++) {
            // drained
        }
    }

    /**
     * Caps the driver's shader compiler threads before the first warm-up links are issued (once per session, render
     * thread). Without a cap the driver picks its own count for dozens of links issued at once. Without a working
     * cap the warm-up stays off for the session.
     */
    private static boolean capCompilerThreads() {
        if (threadsCapped) {
            return true;
        }
        threadsCapped = true;
        int count = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
        GLCapabilities caps = GL.getCapabilities();
        // LWJGL calls these without checking the function pointer, so only call one the driver exposes.
        boolean khr = caps.GL_KHR_parallel_shader_compile && caps.glMaxShaderCompilerThreadsKHR != 0L;
        boolean arb = caps.GL_ARB_parallel_shader_compile && caps.glMaxShaderCompilerThreadsARB != 0L;
        if (!khr && !arb) {
            disable("the driver has no glMaxShaderCompilerThreads to cap its compiler threads with");
            return false;
        }
        clearGlErrors();
        if (khr) {
            KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(count);
        } else {
            ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(count);
        }
        int error = GL11C.glGetError();
        if (error != GL11C.GL_NO_ERROR) {
            disable("the driver rejected glMaxShaderCompilerThreads" + (khr ? "KHR(" : "ARB(") + count + ") (GL error 0x"
                    + Integer.toHexString(error) + ")");
            return false;
        }
        LOGGER.info("[compile] driver shader compiler threads capped at {} ({})", count, khr ? "KHR" : "ARB");
        return true;
    }

    private static boolean linkReady(VisusConfig config) {
        return config.compileParallelLink && gateOpen() && capCompilerThreads();
    }

    private static boolean isAbort(Throwable t) {
        for (int depth = 0; t != null && depth < 16; t = t.getCause(), depth++) {
            if (t == ABORT) {
                return true;
            }
        }
        return false;
    }

    // ---- gates ----------------------------------------------------------------------------------------------------

    private static boolean gateOpen() {
        if (gate == null) {
            gate = evaluateGate();
        }
        return gate;
    }

    private static String driverKey() {
        if (driverKey == null) {
            String vendor = String.valueOf(GL20C.glGetString(GL20C.GL_VENDOR));
            String renderer = String.valueOf(GL20C.glGetString(GL20C.GL_RENDERER));
            String version = String.valueOf(GL20C.glGetString(GL20C.GL_VERSION));
            driverKey = (vendor + " | " + renderer + " | " + version).replaceAll("[=:#!\\\\]", "_");
        }
        return driverKey;
    }

    private static boolean contextsGateOpen() {
        if (contextsGate == null) {
            contextsGate = evaluateContextsGate();
        }
        return contextsGate;
    }

    private static boolean evaluateContextsGate() {
        String verdict = loadDriverFile().getProperty(CONTEXTS_KEY + driverKey());
        if (verdict != null && verdict.startsWith("off")) {
            LOGGER.info("[compile] parallel_contexts off for this driver ({}): {}. Delete config/{} to test again.",
                    driverKey, verdict, DRIVER_FILE);
            return false;
        }
        if (VisusConfig.get().compileAuditMixins) {
            List<String> foreign = foreignMixins();
            if (!foreign.isEmpty()) {
                LOGGER.warn("[compile] parallel_contexts off: other mods patch the shader build classes it runs twice: {}."
                        + " Check them and set compile.auditMixins = false in visus.properties to allow.", foreign);
                return false;
            }
        }
        LOGGER.info("[compile] parallel_contexts on ({})", driverKey);
        return true;
    }

    private static boolean evaluateGate() {
        GLCapabilities caps = GL.getCapabilities();
        driverKey();
        if (!caps.GL_KHR_parallel_shader_compile && !caps.GL_ARB_parallel_shader_compile) {
            LOGGER.info("[compile] parallel_link off: the driver has no parallel shader compile extension ({})", driverKey);
            return false;
        }
        String verdict = loadDriverFile().getProperty(driverKey);
        if (verdict != null && verdict.startsWith("off")) {
            LOGGER.info("[compile] parallel_link off for this driver ({}): {}. Delete config/{} to test again.",
                    driverKey, verdict, DRIVER_FILE);
            return false;
        }
        if (VisusConfig.get().compileAuditMixins) {
            List<String> foreign = foreignMixins();
            if (!foreign.isEmpty()) {
                LOGGER.warn("[compile] parallel_link off: other mods patch the shader build classes it runs twice: {}."
                        + " Check them and set compile.auditMixins = false in visus.properties to allow.", foreign);
                return false;
            }
        }
        LOGGER.info("[compile] parallel_link on ({})", driverKey);
        return true;
    }

    private static List<String> foreignMixins() {
        List<String> foreign = new ArrayList<>();
        for (String target : AUDIT_CLASSES) {
            try {
                ClassInfo info = ClassInfo.forName(target);
                if (info == null) {
                    continue;
                }
                for (IMixinInfo mixin : info.getAppliedMixins()) {
                    String name = mixin.getClassName();
                    boolean allowed = false;
                    for (String prefix : AUDIT_ALLOWED) {
                        if (name.startsWith(prefix)) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {
                        foreign.add(name + " -> " + target.substring(target.lastIndexOf('/') + 1)
                                + " (" + mixin.getConfig().getName() + ")");
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return foreign;
    }

    private static void validate(Build b, double avgLinkMs) {
        if (b.warmContexts > 0) {
            // Capturing includes Oculus' GLSL rewrite, so only the cache hit is checked here.
            if (avgLinkMs > MAX_WARM_LINK_MS) {
                String reason = "off: real links after the context warm-up still waited " + String.format("%.1f", avgLinkMs)
                        + " ms each (the driver does not reuse what other contexts compiled)";
                disable(true, reason);
                Properties properties = loadDriverFile();
                properties.setProperty(CONTEXTS_KEY + driverKey, reason);
                storeDriverFile(properties);
            }
            return;
        }
        double issueMs = b.warmIssueNanos / 1e6 / b.warmIssued;
        String reason = null;
        if (avgLinkMs > MAX_WARM_LINK_MS) {
            reason = "off: real links after the warm-up still waited " + String.format("%.1f", avgLinkMs)
                    + " ms each (no driver cache reuse)";
        } else if (issueMs > MAX_ISSUE_MS) {
            reason = "off: issuing the warm-up links took " + String.format("%.0f", issueMs)
                    + " ms each (the driver compiles while they are issued)";
        }
        if (reason != null) {
            disable(reason);
            Properties properties = loadDriverFile();
            properties.setProperty(driverKey, reason);
            storeDriverFile(properties);
        }
    }

    private static void disable(String reason) {
        gate = false;
        LOGGER.warn("[compile] parallel_link switched off: {}", reason);
    }

    private static void disable(boolean contexts, String reason) {
        if (!contexts) {
            disable(reason);
            return;
        }
        contextsGate = false;
        LOGGER.warn("[compile] parallel_contexts switched off: {}", reason);
    }

    private static Properties loadDriverFile() {
        Properties properties = new Properties();
        Path file = VisusConfig.configDir().resolve(DRIVER_FILE);
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                LOGGER.warn("[compile] could not read {}: {}", DRIVER_FILE, e.toString());
            }
        }
        return properties;
    }

    private static void storeDriverFile(Properties properties) {
        try (OutputStream out = Files.newOutputStream(VisusConfig.configDir().resolve(DRIVER_FILE))) {
            properties.store(out, "Visus compile service results per GPU driver. Delete to test again.");
        } catch (IOException e) {
            LOGGER.warn("[compile] could not write {}: {}", DRIVER_FILE, e.toString());
        }
    }

    private static String ms(double ms) {
        return ms >= 1000 ? String.format("%.1f s", ms / 1000) : String.format("%.0f ms", ms);
    }
}
