// Ported from RatOptimization's oculus_shaders module (MIT, Copyright (c) 2026 SolarBiscuit) into the Visus
// compile service (architecture §4.4, §5 L2). Visus changes are listed in docs/CHANGES-FROM-OCULUS.md.
package dev.visus.compile;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Hidden GL contexts for the parallel_contexts warm-up, one per worker thread, in Minecraft's share group and with its
 * context version and profile. A worker gets a copy of one program's final sources (stages and attribute bindings),
 * compiles and links its own program from them, waits for the link and deletes everything it made. The driver keeps
 * the compiled result in its shader cache, which the real build on the render thread then hits. Workers never touch an
 * object they did not create, and nothing they make is used for drawing.
 * <p>
 * Started once per session from the render thread. Window creation goes through GLFW (Ixeris runs it on its main
 * thread); each worker makes its own context current on itself.
 */
final class CompileContexts {

    private static final Logger LOGGER = LoggerFactory.getLogger("Visus");
    private static final long START_LIMIT_SECONDS = 10;

    record Stage(int type, String source) {
    }

    record Attrib(int index, String name) {
    }

    record Job(String name, List<Stage> stages, List<Attrib> attribs) {
    }

    private record Task(Job job, CompletableFuture<Void> done) {
    }

    private static final LinkedBlockingQueue<Task> queue = new LinkedBlockingQueue<>();
    /** Tells one worker to release its context and end (startup failure only). */
    private static final Task STOP = new Task(null, null);
    private static int started;
    private static boolean failed;

    private CompileContexts() {
    }

    static int count() {
        return started;
    }

    /**
     * Creates the contexts and their workers on first use (render thread). Returns false, for the rest of the session,
     * if a context could not be created or made current.
     */
    static boolean start(int count) {
        if (started > 0) {
            return true;
        }
        if (failed) {
            return false;
        }
        long share = Minecraft.getInstance().getWindow().getWindow();
        List<Long> windows = new ArrayList<>(count);
        try {
            int major = GLFW.glfwGetWindowAttrib(share, GLFW.GLFW_CONTEXT_VERSION_MAJOR);
            int minor = GLFW.glfwGetWindowAttrib(share, GLFW.GLFW_CONTEXT_VERSION_MINOR);
            int profile = GLFW.glfwGetWindowAttrib(share, GLFW.GLFW_OPENGL_PROFILE);
            int forward = GLFW.glfwGetWindowAttrib(share, GLFW.GLFW_OPENGL_FORWARD_COMPAT);
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, Math.max(3, major));
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, major > 3 ? minor : Math.max(2, minor));
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, profile != 0 ? profile : GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, forward != 0 ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            for (int i = 0; i < count; i++) {
                long window = GLFW.glfwCreateWindow(1, 1, "Visus shader compiler", 0L, share);
                if (window == 0L) {
                    break;
                }
                windows.add(window);
            }
        } catch (Throwable t) {
            LOGGER.warn("[compile] could not create shader compiler contexts: {}", t.toString());
        } finally {
            GLFW.glfwDefaultWindowHints();
        }
        if (windows.isEmpty()) {
            return fail("the driver created no shared context", windows);
        }
        List<CompletableFuture<Boolean>> ready = new ArrayList<>(windows.size());
        List<Thread> workers = new ArrayList<>(windows.size());
        for (int i = 0; i < windows.size(); i++) {
            long window = windows.get(i);
            CompletableFuture<Boolean> current = new CompletableFuture<>();
            ready.add(current);
            Thread worker = new Thread(() -> run(window, current), "Visus shader compiler #" + (i + 1));
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
        try {
            for (CompletableFuture<Boolean> current : ready) {
                if (!current.get(START_LIMIT_SECONDS, TimeUnit.SECONDS)) {
                    return stop("a worker could not make its context current", windows, workers);
                }
            }
        } catch (Throwable t) {
            return stop("a worker did not start: " + t, windows, workers);
        }
        started = windows.size();
        LOGGER.info("[compile] {} shader compiler contexts started", started);
        return true;
    }

    /** Queues one program for a worker. The future completes once the worker's link is done and its copy deleted. */
    static CompletableFuture<Void> submit(Job job) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        queue.add(new Task(job, done));
        return done;
    }

    /**
     * Startup failed after the contexts were created: every worker releases its context and ends, then the hidden
     * windows are destroyed (before, they and their threads stayed for the whole session). A window whose worker does
     * not end in time is left alone, since destroying a context current on another thread is not allowed.
     */
    private static boolean stop(String reason, List<Long> windows, List<Thread> workers) {
        for (int i = 0; i < workers.size(); i++) {
            queue.add(STOP);
        }
        List<Long> released = new ArrayList<>(windows.size());
        for (int i = 0; i < workers.size(); i++) {
            try {
                workers.get(i).join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!workers.get(i).isAlive()) {
                released.add(windows.get(i));
            }
        }
        return fail(reason, released);
    }

    private static boolean fail(String reason, List<Long> windows) {
        failed = true;
        for (long window : windows) {
            try {
                GLFW.glfwDestroyWindow(window);
            } catch (Throwable ignored) {
            }
        }
        LOGGER.warn("[compile] parallel_contexts switched off for this session: {}", reason);
        return false;
    }

    private static void run(long window, CompletableFuture<Boolean> ready) {
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
        } catch (Throwable t) {
            LOGGER.warn("[compile] {}: {}", Thread.currentThread().getName(), t.toString());
            ready.complete(false);
            return;
        }
        ready.complete(true);
        while (true) {
            Task task;
            try {
                task = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            if (task == STOP) {
                GLFW.glfwMakeContextCurrent(0L);
                return;
            }
            try {
                compile(task.job());
                task.done().complete(null);
            } catch (Throwable t) {
                task.done().completeExceptionally(t);
            }
        }
    }

    private static void compile(Job job) {
        int program = GL20C.glCreateProgram();
        int[] shaders = new int[job.stages().size()];
        try {
            for (int i = 0; i < shaders.length; i++) {
                Stage stage = job.stages().get(i);
                shaders[i] = GL20C.glCreateShader(stage.type());
                GL20C.glShaderSource(shaders[i], stage.source());
                GL20C.glCompileShader(shaders[i]);
                GL20C.glAttachShader(program, shaders[i]);
            }
            for (Attrib attrib : job.attribs()) {
                GL20C.glBindAttribLocation(program, attrib.index(), attrib.name());
            }
            GL20C.glLinkProgram(program);
            // Waits for the driver's compile, on this worker only.
            GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS);
        } finally {
            for (int shader : shaders) {
                if (shader != 0) {
                    GL20C.glDetachShader(program, shader);
                    GL20C.glDeleteShader(shader);
                }
            }
            GL20C.glDeleteProgram(program);
        }
    }
}
