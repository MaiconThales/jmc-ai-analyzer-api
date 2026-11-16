package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Starts one configured MCP child process and exposes its three stdio pipes.
 *
 * <p>There is deliberately no protocol handshake here: the local MCP contract
 * is not available yet. Successful process creation and a live process are the
 * only safe readiness signals for B4.1. The streams remain untouched for the
 * future protocol adapter. stderr is never merged into stdout.</p>
 */
@Component
public class McpStdioProcessBridge implements McpStdioClient {

    private final McpClientProperties properties;
    private final McpProcessLauncher launcher;
    private Process process;
    private ExecutorService stderrExecutor;
    private Future<?> stderrDrain;

    @Autowired
    public McpStdioProcessBridge(McpClientProperties properties) {
        this(properties, command -> new ProcessBuilder(command).redirectErrorStream(false).start());
    }

    McpStdioProcessBridge(McpClientProperties properties, McpProcessLauncher launcher) {
        this.properties = properties;
        this.launcher = launcher;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            throw new IllegalStateException("MCP process is already started");
        }
        if (process != null) {
            stopStderrDrain();
            cleanupProcess(process);
            process = null;
        }
        validateConfiguration(properties);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mcp-process-starter");
            thread.setDaemon(true);
            return thread;
        });
        StartAttempt attempt = new StartAttempt();
        Future<?> future = executor.submit(() -> {
            attempt.complete(launcher.start(commandLine(properties)));
            return null;
        });
        try {
            future.get(properties.getStartupTimeout().toMillis(), TimeUnit.MILLISECONDS);
            process = attempt.take();
            if (process == null) {
                throw new IllegalStateException("MCP process was cancelled during startup");
            }
            if (!process.isAlive()) {
                cleanupProcess(process);
                process = null;
                throw new IllegalStateException("MCP process exited during startup");
            }
            startStderrDrain(process);
        } catch (TimeoutException exception) {
            attempt.cancel();
            future.cancel(true);
            throw new IllegalStateException("Timed out starting MCP process", exception);
        } catch (InterruptedException exception) {
            attempt.cancel();
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting MCP process", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Could not start MCP process", exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    @Override
    public synchronized OutputStream stdin() {
        return requireProcess().getOutputStream();
    }

    @Override
    public synchronized InputStream stdout() {
        return requireProcess().getInputStream();
    }

    @Override
    public synchronized InputStream stderr() {
        return requireProcess().getErrorStream();
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }
        Process closing = process;
        stopStderrDrain();
        cleanupProcess(closing);
        try {
            if (!closing.waitFor(1, TimeUnit.SECONDS)) {
                closing.destroyForcibly();
                closing.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            closing.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            process = null;
        }
    }

    @Override
    public void invalidate() {
        close();
    }

    private Process requireProcess() {
        if (process == null) {
            throw new IllegalStateException("MCP process has not been started");
        }
        return process;
    }

    private static List<String> commandLine(McpClientProperties properties) {
        List<String> command = new ArrayList<>();
        command.add(properties.getCommand());
        command.addAll(properties.getArguments());
        return List.copyOf(command);
    }

    private static void validateConfiguration(McpClientProperties properties) {
        String command = properties.getCommand();
        if (command == null || command.isBlank() || containsControlCharacter(command)) {
            throw new IllegalArgumentException("mcp.command must be a non-empty executable name or path");
        }
        if (properties.getStartupTimeout() == null || properties.getStartupTimeout().isZero()
                || properties.getStartupTimeout().isNegative()) {
            throw new IllegalArgumentException("mcp.startup-timeout must be positive");
        }
        for (String argument : properties.getArguments()) {
            if (argument == null || containsControlCharacter(argument)) {
                throw new IllegalArgumentException("mcp.arguments cannot contain null or control characters");
            }
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl(character));
    }

    private static void closeQuietly(java.io.Closeable stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Closing a child pipe is best effort during shutdown.
        }
    }

    private static void cleanupProcess(Process process) {
        closeQuietly(process.getOutputStream());
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * A child is allowed to write arbitrarily much diagnostics.  If stderr is
     * not consumed independently, the OS pipe eventually fills and the child
     * can stop producing protocol responses on stdout.  It is intentionally a
     * silent sink: diagnostics must never become MCP protocol bytes or logs on
     * stdout.
     */
    private void startStderrDrain(Process child) {
        stderrExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mcp-process-stderr-drain");
            thread.setDaemon(true);
            return thread;
        });
        stderrDrain = stderrExecutor.submit(() -> {
            try (InputStream errors = child.getErrorStream()) {
                errors.transferTo(java.io.OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // Pipe closure is expected during process shutdown.
            }
        });
    }

    private void stopStderrDrain() {
        if (stderrDrain != null) {
            stderrDrain.cancel(true);
            stderrDrain = null;
        }
        if (stderrExecutor != null) {
            stderrExecutor.shutdownNow();
            try {
                stderrExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            stderrExecutor = null;
        }
    }

    /**
     * Coordinates the timeout path with a launcher that may return after its
     * calling thread was interrupted. In particular, a process returned after
     * cancellation must never escape without being destroyed.
     */
    private static final class StartAttempt {
        private boolean cancelled;
        private boolean handedOff;
        private Process candidate;

        synchronized void complete(Process candidate) {
            if (cancelled) {
                cleanupProcess(candidate);
                return;
            }
            this.candidate = candidate;
        }

        synchronized Process take() {
            if (cancelled) {
                return null;
            }
            handedOff = true;
            Process result = candidate;
            candidate = null;
            return result;
        }

        synchronized void cancel() {
            cancelled = true;
            if (candidate != null && !handedOff) {
                cleanupProcess(candidate);
                candidate = null;
            }
        }
    }
}
