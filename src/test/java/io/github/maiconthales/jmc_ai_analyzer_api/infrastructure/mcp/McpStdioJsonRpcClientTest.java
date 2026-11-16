package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.node.JsonNodeFactory;

class McpStdioJsonRpcClientTest {

    @Test
    void performsInitializeNotificationAndToolCallWithLineFramingAndUnorderedIds() throws Exception {
        String responses = "{\"jsonrpc\":\"2.0\",\"id\":99,\"result\":{}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"structuredContent\":{\"ok\":true}}}\n";
        GatedStdioProcess process = new GatedStdioProcess(responses);
        McpClientProperties properties = properties("lines");

        var result = new McpStdioJsonRpcClient(process, properties)
                .callTool("thread-dump", JsonNodeFactory.instance.objectNode());

        assertEquals(true, result.get("ok").asBoolean());
        String wire = process.written.toString(StandardCharsets.UTF_8);
        assertEquals(3, wire.lines().count());
        // No diagnostics or protocol logging are written outside the JSON-RPC wire.
        assertEquals(0, process.stderr.size());
    }

    @Test
    void supportsContentLengthFramingAndSurfacesJsonRpcErrors() {
        String responses = frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}")
                + frame("{\"jsonrpc\":\"2.0\",\"id\":2,\"error\":{\"code\":-32601,\"message\":\"missing\"}}");
        FakeStdioProcess process = new FakeStdioProcess(responses);
        McpClientProperties properties = properties("content-length");

        assertThrows(IllegalStateException.class, () -> new McpStdioJsonRpcClient(process, properties)
                .callTool("missing", JsonNodeFactory.instance.objectNode()));
        String wire = process.written.toString(StandardCharsets.UTF_8);
        assertEquals(3, wire.split("Content-Length:", -1).length - 1);
    }

    @Test
    void rejectsUnsupportedTransportInsteadOfSilentlyUsingLineFraming() {
        McpClientProperties properties = new McpClientProperties();
        assertThrows(IllegalArgumentException.class, () -> properties.setTransport("json"));
    }

    @Test
    void acceptsMultipleContentLengthFramesWithoutTreatingFragmentsAsFrames() {
        String first = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
        String second = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"ok\":true}}";
        FakeStdioProcess process = new FakeStdioProcess(frame(first) + frame(second));
        var result = new McpStdioJsonRpcClient(process, properties("content-length"))
                .callTool("tool", JsonNodeFactory.instance.objectNode());
        assertEquals(true, result.get("ok").asBoolean());
    }

    @Test
    void rejectsCompleteJsonAtEofWhenLineFrameHasNoNewline() {
        FakeStdioProcess process = new FakeStdioProcess(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");

        assertThrows(IllegalStateException.class, () -> new McpStdioJsonRpcClient(process, properties("lines"))
                .callTool("tool", JsonNodeFactory.instance.objectNode()));
    }

    @Test
    void rejectsPartialJsonAtEofWhenLineFrameHasNoNewline() {
        FakeStdioProcess process = new FakeStdioProcess("{\"jsonrpc\":\"2.0\",\"id\":1");

        assertThrows(IllegalStateException.class, () -> new McpStdioJsonRpcClient(process, properties("lines"))
                .callTool("tool", JsonNodeFactory.instance.objectNode()));
    }

    @Test
    void readerFailureCleansOldGenerationBeforeRestartingTheTransport() {
        RestartableStdioProcess process = new RestartableStdioProcess();
        McpStdioJsonRpcClient client = new McpStdioJsonRpcClient(process, properties("lines"));

        assertThrows(IllegalStateException.class,
                () -> client.callTool("first", JsonNodeFactory.instance.objectNode()));
        var result = client.callTool("second", JsonNodeFactory.instance.objectNode());

        assertEquals(true, result.get("restarted").asBoolean());
        assertEquals(2, process.starts.get());
        assertTrue(process.invalidations.get() >= 1);
    }

    @Test
    void cancellingOneMultiplexedRequestDoesNotInvalidateTheOtherRequests() throws Exception {
        String responses = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n";
        GatedStdioProcess process = new GatedStdioProcess(responses);
        McpStdioJsonRpcClient client = new McpStdioJsonRpcClient(process, properties("lines"));

        var cancelled = client.callToolAsync("cancel-me", JsonNodeFactory.instance.objectNode());
        cancelled.cancel();
        process.append("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"value\":\"late\"}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"value\":3}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"value\":4}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"value\":5}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":6,\"result\":{\"value\":6}}\n");
        var success3 = client.callToolAsync("three", JsonNodeFactory.instance.objectNode());
        var success4 = client.callToolAsync("four", JsonNodeFactory.instance.objectNode());
        var success5 = client.callToolAsync("five", JsonNodeFactory.instance.objectNode());
        var success6 = client.callToolAsync("six", JsonNodeFactory.instance.objectNode());
        cancelled.cancel();

        assertThrows(CancellationException.class, cancelled.future()::get);
        assertEquals(3, success3.future().get().get("value").asInt());
        assertEquals(4, success4.future().get().get("value").asInt());
        assertEquals(5, success5.future().get().get("value").asInt());
        assertEquals(6, success6.future().get().get("value").asInt());
        success3.close(); success4.close(); success5.close(); success6.close();
    }

    @Test
    void rejectsOversizedLineAndContentLengthFrames() {
        McpClientProperties lines = properties("lines");
        lines.setMaxFrameSize(32);
        assertThrows(IllegalStateException.class, () -> new McpStdioJsonRpcClient(
                new FakeStdioProcess("123456789012345678901234567890123\n"), lines)
                .callTool("tool", JsonNodeFactory.instance.objectNode()));

        McpClientProperties length = properties("content-length");
        length.setMaxFrameSize(8);
        assertThrows(IllegalStateException.class, () -> new McpStdioJsonRpcClient(
                new FakeStdioProcess("Content-Length: 9\r\n\r\n123456789"), length)
                .callTool("tool", JsonNodeFactory.instance.objectNode()));
    }

    @Test
    void jsonRpcErrorIsRequestLocalAndDoesNotInvalidateMultiplexedTransport() throws Exception {
        String responses = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"error\":{\"code\":-32000,\"message\":\"tool failed\"}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"structuredContent\":{\"value\":3}}}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"structuredContent\":{\"value\":4}}}\n";
        GatedStdioProcess process = new GatedStdioProcess(responses);
        McpStdioJsonRpcClient client = new McpStdioJsonRpcClient(process, properties("lines"));

        var failed = client.callToolAsync("failed", JsonNodeFactory.instance.objectNode());
        var success3 = client.callToolAsync("three", JsonNodeFactory.instance.objectNode());
        var success4 = client.callToolAsync("four", JsonNodeFactory.instance.objectNode());

        ExecutionException error = assertThrows(ExecutionException.class, failed.future()::get);
        assertTrue(error.getCause() instanceof McpJsonRpcException);
        assertEquals(3, success3.future().get().get("value").asInt());
        assertEquals(4, success4.future().get().get("value").asInt());
        assertTrue(process.running);
        success3.close(); success4.close();
    }

    @Test
    void decodesValidUtf8LineFrames() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"structuredContent\":{\"message\":\"ação\"}}}\n";
        GatedStdioProcess process;
        try {
            process = new GatedStdioProcess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n" + body);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        var result = new McpStdioJsonRpcClient(process, properties("lines"))
                .callTool("utf8", JsonNodeFactory.instance.objectNode());
        assertEquals("ação", result.get("message").asText());
    }

    @Test
    void enforcesLineFrameLimitInUtf8Bytes() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"éééééééé\":true}}\n";
        McpClientProperties properties = properties("lines");
        properties.setMaxFrameSize(body.getBytes(StandardCharsets.UTF_8).length - 1);
        assertThrows(IllegalStateException.class, () -> new McpStdioJsonRpcClient(
                new FakeStdioProcess(body), properties)
                .callTool("tool", JsonNodeFactory.instance.objectNode()));
    }

    private static McpClientProperties properties(String transport) {
        McpClientProperties properties = new McpClientProperties();
        properties.setEnabled(true);
        properties.setTransport(transport);
        return properties;
    }

    private static String frame(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return "Content-Length: " + bytes.length + "\r\n\r\n" + body;
    }

    private static final class FakeStdioProcess implements McpStdioClient {
        private final InputStream stdout;
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private boolean running;

        private FakeStdioProcess(String responses) {
            stdout = new ByteArrayInputStream(responses.getBytes(StandardCharsets.UTF_8));
        }

        @Override public void start() { running = true; }
        @Override public boolean isRunning() { return running; }
        @Override public OutputStream stdin() { return written; }
        @Override public InputStream stdout() { return stdout; }
        @Override public InputStream stderr() { return new ByteArrayInputStream(new byte[0]); }
        @Override public void close() { }
    }

    private static final class GatedStdioProcess implements McpStdioClient {
        private final PipedInputStream stdout = new PipedInputStream();
        private final PipedOutputStream responseWriter;
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private volatile boolean running;

        private GatedStdioProcess(String initial) throws IOException {
            responseWriter = new PipedOutputStream(stdout);
            append(initial);
        }

        private void append(String value) throws IOException {
            responseWriter.write(value.getBytes(StandardCharsets.UTF_8));
            responseWriter.flush();
        }

        @Override public void start() { running = true; }
        @Override public boolean isRunning() { return running; }
        @Override public OutputStream stdin() { return written; }
        @Override public InputStream stdout() { return stdout; }
        @Override public InputStream stderr() { return new ByteArrayInputStream(stderr.toByteArray()); }
        @Override public void close() { running = false; try { responseWriter.close(); } catch (IOException ignored) { } }
    }

    private static final class RestartableStdioProcess implements McpStdioClient {
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger invalidations = new AtomicInteger();
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private volatile boolean running;
        private InputStream stdout;

        @Override public void start() {
            if (running) throw new IllegalStateException("already started");
            if (starts.incrementAndGet() == 1) {
                stdout = new InputStream() {
                    @Override public int read() throws IOException {
                        throw new IOException("old reader failed");
                    }
                };
            } else {
                stdout = new ByteArrayInputStream((
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}\n"
                        + "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"structuredContent\":{\"restarted\":true}}}\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            running = true;
        }

        @Override public boolean isRunning() { return running; }
        @Override public OutputStream stdin() { return written; }
        @Override public InputStream stdout() { return stdout; }
        @Override public InputStream stderr() { return InputStream.nullInputStream(); }
        @Override public void close() { running = false; }
        @Override public void invalidate() {
            invalidations.incrementAndGet();
            running = false;
        }
    }
}
