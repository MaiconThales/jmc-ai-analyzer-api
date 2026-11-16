package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class McpStdioProcessBridgeTest {

    @Test
    void startsProcessWithSeparateStdioAndClosesIt() throws Exception {
        McpStdioProcessBridge bridge = bridge("ready");

        bridge.start();

        assertTrue(bridge.isRunning());
        assertEquals("MCP_READY", line(bridge.stdout()));
        // stderr is consumed by the bridge and is never exposed as protocol
        // output.  In particular, a verbose child cannot block stdout.
        assertTrue(bridge.stderr() != bridge.stdout());
        bridge.close();

        assertFalse(bridge.isRunning());
    }

    @Test
    void startupTimeoutDoesNotLeaveAStarterThreadRunning() {
        McpClientProperties properties = properties();
        properties.setStartupTimeout(Duration.ofMillis(30));
        McpProcessLauncher blockingLauncher = ignored -> {
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            throw new java.io.IOException("interrupted");
        };
        McpStdioProcessBridge bridge = new McpStdioProcessBridge(properties, blockingLauncher);

        assertThrows(IllegalStateException.class, bridge::start);
        assertFalse(bridge.isRunning());
    }

    @Test
    void destroysProcessReturnedAfterStartupTimeout() throws Exception {
        McpClientProperties properties = properties();
        properties.setStartupTimeout(Duration.ofMillis(30));
        LateProcess process = new LateProcess();
        CountDownLatch interrupted = new CountDownLatch(1);
        McpProcessLauncher ignoringInterruptLauncher = command -> {
            try {
                interrupted.await();
            } catch (InterruptedException cancellation) {
                // Deliberately return a process after cancellation.
            }
            return process;
        };
        McpStdioProcessBridge bridge = new McpStdioProcessBridge(properties, ignoringInterruptLauncher);

        assertThrows(IllegalStateException.class, bridge::start);
        interrupted.countDown();

        assertTrue(process.destroyed.await(2, TimeUnit.SECONDS));
        assertTrue(process.destroyCalled);
        assertTrue(process.stdin.closed);
        assertTrue(process.stdout.closed);
        assertTrue(process.stderr.closed);
        assertFalse(bridge.isRunning());
    }

    @Test
    void startupDeathClosesAllStreamsAndDestroysProcess() {
        McpClientProperties properties = properties();
        TrackableProcess process = new TrackableProcess(false);
        McpStdioProcessBridge bridge = new McpStdioProcessBridge(properties, ignored -> process);

        assertThrows(IllegalStateException.class, bridge::start);

        assertTrue(process.destroyCalled);
        assertTrue(process.stdin.closed);
        assertTrue(process.stdout.closed);
        assertTrue(process.stderr.closed);
        assertFalse(bridge.isRunning());
    }

    @Test
    void restartCleansPreviouslyStartedProcessThatDied() {
        McpClientProperties properties = properties();
        TrackableProcess first = new TrackableProcess(true);
        TrackableProcess second = new TrackableProcess(true);
        AtomicInteger launches = new AtomicInteger();
        McpStdioProcessBridge bridge = new McpStdioProcessBridge(properties,
                ignored -> launches.getAndIncrement() == 0 ? first : second);

        bridge.start();
        first.markDead();
        bridge.start();

        assertTrue(first.destroyCalled);
        assertTrue(first.stdin.closed);
        assertTrue(first.stdout.closed);
        assertTrue(first.stderr.closed);
        assertTrue(bridge.isRunning());
        bridge.close();
    }

    @Test
    void rejectsControlCharactersInConfiguredCommandAndArguments() {
        McpClientProperties properties = properties();
        properties.setCommand("java\n-not-a-shell-command");
        McpStdioProcessBridge bridge = new McpStdioProcessBridge(properties, ignored -> null);

        assertThrows(IllegalArgumentException.class, bridge::start);
    }

    @Test
    void drainsLargeStderrWithoutBlockingStdout() throws Exception {
        McpStdioProcessBridge bridge = bridge("flood");
        bridge.start();

        assertEquals("MCP_RESPONSE", line(bridge.stdout()));
        bridge.close();
    }

    private static McpStdioProcessBridge bridge(String mode) {
        McpClientProperties properties = properties();
        properties.setArguments(List.of(
                "-cp", System.getProperty("java.class.path"),
                MockMcpProcess.class.getName(), mode));
        return new McpStdioProcessBridge(properties);
    }

    private static McpClientProperties properties() {
        McpClientProperties properties = new McpClientProperties();
        String java = System.getProperty("java.home") + "/bin/java";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            java += ".exe";
        }
        properties.setCommand(java);
        properties.setStartupTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static String line(java.io.InputStream stream) throws Exception {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).readLine();
    }

    public static final class MockMcpProcess {
        private MockMcpProcess() {
        }

        public static void main(String[] args) throws Exception {
            if ("ready".equals(args[0])) {
                System.out.println("MCP_READY");
                System.out.flush();
                System.err.println("MCP_DIAGNOSTIC");
                System.err.flush();
                Thread.sleep(Duration.ofSeconds(2));
            } else if ("flood".equals(args[0])) {
                byte[] diagnostics = new byte[1024 * 1024];
                java.util.Arrays.fill(diagnostics, (byte) 'x');
                System.err.write(diagnostics);
                System.err.flush();
                System.out.println("MCP_RESPONSE");
                System.out.flush();
                Thread.sleep(Duration.ofSeconds(2));
            }
        }
    }

    private static final class LateProcess extends Process {
        private final CountDownLatch destroyed = new CountDownLatch(1);
        private volatile boolean destroyCalled;
        private final TrackableOutputStream stdin = new TrackableOutputStream();
        private final TrackableInputStream stdout = new TrackableInputStream();
        private final TrackableInputStream stderr = new TrackableInputStream();

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            destroyed.await();
            return 143;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            return destroyed.await(timeout, unit);
        }

        @Override
        public int exitValue() {
            if (!destroyCalled) {
                throw new IllegalThreadStateException("process is alive");
            }
            return 143;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
            destroyed.countDown();
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyCalled;
        }
    }

    private static final class TrackableProcess extends Process {
        private final TrackableOutputStream stdin = new TrackableOutputStream();
        private final TrackableInputStream stdout = new TrackableInputStream();
        private final TrackableInputStream stderr = new TrackableInputStream();
        private volatile boolean alive;
        private volatile boolean destroyCalled;

        private TrackableProcess(boolean alive) {
            this.alive = alive;
        }

        private void markDead() {
            alive = false;
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            alive = false;
            return 143;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            alive = false;
            return true;
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("process is alive");
            }
            return 143;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }

    private static final class TrackableInputStream extends ByteArrayInputStream {
        private volatile boolean closed;

        private TrackableInputStream() {
            super(new byte[0]);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackableOutputStream extends OutputStream {
        private volatile boolean closed;

        @Override
        public void write(int value) {
            if (closed) {
                throw new IllegalStateException("stream is closed");
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
