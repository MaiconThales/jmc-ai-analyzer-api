package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Minimal MCP STDIO JSON-RPC 2.0 adapter. It deliberately writes no diagnostics to stdout. */
public final class McpStdioJsonRpcClient implements McpProtocolClient {
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private final McpStdioClient process;
    private final McpClientProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong ids = new AtomicLong();
    private final Object lifecycleLock = new Object();
    private final Object initializationLock = new Object();
    /** Only this lock is held while bytes are written; responses are never read here. */
    private final Object writeLock = new Object();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Set<Long> cancelled = ConcurrentHashMap.newKeySet();
    // Kept for a response that was already buffered by a test transport (or
    // by a peer that flushed unusually early) before the request future was
    // registered. Normal peers cannot need this, but dropping such a frame
    // would make correlation depend on thread scheduling.
    private static final int MAX_EARLY_RESPONSES = 64;
    private final Map<Long, JsonNode> earlyResponses = new LinkedHashMap<>();
    private volatile BufferedInputStream input;
    private volatile BufferedOutputStream output;
    private volatile boolean initialized;
    private volatile boolean invalidated;
    private long generation;
    private ExecutorService readerExecutor;

    public McpStdioJsonRpcClient(McpStdioClient process, McpClientProperties properties) {
        this.process = process;
        this.properties = properties;
    }

    @Override
    public JsonNode callTool(String toolName, JsonNode arguments) {
        if (!properties.isEnabled()) throw new IllegalStateException("MCP client is disabled by configuration");
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("toolName must not be blank");
        try {
            ensureInitialized();
            ObjectNode params = mapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments == null ? mapper.createObjectNode() : arguments);
            JsonNode result = request("tools/call", params);
            if (result.has("isError") && result.get("isError").asBoolean())
                throw new IllegalStateException("MCP tool returned an error: " + result);
            return result.has("structuredContent") ? result.get("structuredContent") : result;
        } catch (IOException | InterruptedException exception) {
            invalidate();
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP STDIO JSON-RPC call failed", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof McpJsonRpcException jsonRpcError) throw jsonRpcError;
            invalidate();
            throw new IllegalStateException("MCP STDIO JSON-RPC call failed", exception);
        }
    }

    /** Starts a request without coupling cancellation to the shared STDIO session. */
    @Override
    public McpCallHandle callToolAsync(String toolName, JsonNode arguments) {
        if (!properties.isEnabled()) throw new IllegalStateException("MCP client is disabled by configuration");
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("toolName must not be blank");
        CompletableFuture<JsonNode> wireResult = new CompletableFuture<>();
        final long[] requestId = {-1};
        try {
            ensureInitialized();
            ObjectNode params = mapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments == null ? mapper.createObjectNode() : arguments);
            requestId[0] = registerAndWrite("tools/call", params, wireResult);
        } catch (Exception failure) {
            wireResult.completeExceptionally(failure);
        }
        CompletableFuture<JsonNode> result = wireResult.thenApply(value -> {
            if (value.has("isError") && value.get("isError").asBoolean())
                throw new CompletionException(new IllegalStateException("MCP tool returned an error: " + value));
            return value.has("structuredContent") ? value.get("structuredContent") : value;
        });
        long id = requestId[0];
        return new McpCallHandle(result, () -> { }, () -> {
            if (id > 0) cancelRequest(id, wireResult);
        });
    }

    private void ensureInitialized() throws IOException, InterruptedException, ExecutionException {
        synchronized (initializationLock) {
            synchronized (lifecycleLock) {
                ensureStarted();
            }
            if (!initialized) {
                try {
                    request("initialize", initializeParams());
                } catch (McpJsonRpcException exception) {
                    // An initialize error is a session/protocol failure, not a
                    // request-local tool error. Do not reuse a poisoned session.
                    invalidate();
                    throw exception;
                }
                notification("notifications/initialized", mapper.createObjectNode());
                synchronized (lifecycleLock) {
                    if (invalidated) throw new IOException("MCP client was invalidated during initialization");
                    initialized = true;
                }
            }
        }
    }

    private void ensureStarted() {
        // A live child is not sufficient evidence that the transport is usable:
        // its reader may already have terminated. Treat a missing/terminated
        // reader as a dead generation rather than reusing a half-torn-down one.
        if (!process.isRunning() || invalidated || output == null
                || readerExecutor == null || readerExecutor.isShutdown()) {
            process.start();
            input = new BufferedInputStream(process.stdout());
            output = new BufferedOutputStream(process.stdin());
            initialized = false;
            invalidated = false;
            generation++;
            long readerGeneration = generation;
            readerExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "mcp-stdout-reader");
                thread.setDaemon(true);
                return thread;
            });
            readerExecutor.submit(() -> readResponses(readerGeneration));
        }
    }

    private ObjectNode initializeParams() {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", mapper.createObjectNode());
        ObjectNode info = mapper.createObjectNode();
        info.put("name", "jmc-ai-analyzer-api");
        info.put("version", "0.0.1");
        params.set("clientInfo", info);
        return params;
    }

    private JsonNode request(String method, JsonNode params)
            throws IOException, InterruptedException, ExecutionException {
        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        long id = registerAndWrite(method, params, response);
        try {
            return response.get();
        } finally {
            pending.remove(id, response);
            synchronized (lifecycleLock) {
                lifecycleLock.notifyAll();
            }
        }
    }

    private long registerAndWrite(String method, JsonNode params, CompletableFuture<JsonNode> response) throws IOException {
        long id = ids.incrementAndGet();
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0"); request.put("id", id); request.put("method", method);
        request.set("params", params);
        synchronized (lifecycleLock) {
            if (invalidated || output == null || !process.isRunning())
                throw new IOException("MCP client is not active");
            // Registration and write are atomic with respect to invalidate().
            pending.put(id, response);
            try {
                writeLocked(request);
            } catch (IOException | RuntimeException failure) {
                pending.remove(id, response);
                response.completeExceptionally(failure);
                throw failure;
            }
            JsonNode early = earlyResponses.remove(id);
            if (early != null) completeResponse(response, early);
            lifecycleLock.notifyAll();
        }
        return id;
    }

    private void cancelRequest(long id, CompletableFuture<JsonNode> response) {
        synchronized (lifecycleLock) {
            if (pending.remove(id, response)) cancelled.add(id);
            earlyResponses.remove(id);
            lifecycleLock.notifyAll();
        }
        response.cancel(false);
    }

    private void readResponses(long readerGeneration) {
        try {
            while (true) {
                // Do not race a caller that is between initialization and the
                // next multiplexed request. A peer that closes an idle stream
                // must not tear down the generation before that request is
                // registered.
                synchronized (lifecycleLock) {
                    while (pending.isEmpty() && !invalidated && generation == readerGeneration) {
                        lifecycleLock.wait();
                    }
                    if (invalidated || generation != readerGeneration) return;
                }
                JsonNode response = read();
                validateEnvelope(response);
                JsonNode idNode = response.get("id");
                // Server notifications are valid and cannot complete a request.
                if (idNode == null) continue;
                long id = idNode.longValue();
                synchronized (lifecycleLock) {
                    CompletableFuture<JsonNode> future = pending.remove(id);
                    if (future != null) completeResponse(future, response);
                    else if (!cancelled.remove(id) && earlyResponses.size() < MAX_EARLY_RESPONSES)
                        earlyResponses.put(id, response);
                }
            }
        } catch (Throwable failure) {
            synchronized (lifecycleLock) {
                // A reader can finish after a restart has already installed a
                // new transport. It no longer owns that transport and must not
                // tear it down or fail its pending requests.
                if (generation != readerGeneration) return;
                invalidated = true;
                initialized = false;
                generation++;
                failPending(failure);
                earlyResponses.clear();
                cancelled.clear();
                input = null;
                output = null;
                // Keep teardown under lifecycleLock. A restart cannot observe
                // the invalidated state until the old process is fully closed,
                // so it cannot race with this cleanup or hit "already started".
                process.invalidate();
                if (readerExecutor != null) {
                    readerExecutor.shutdownNow();
                    readerExecutor = null;
                }
            }
        }
    }

    /** Validates the JSON-RPC envelope before correlation can hide a protocol error. */
    private static void validateEnvelope(JsonNode envelope) throws IOException {
        if (envelope == null || !envelope.isObject()
                || !envelope.path("jsonrpc").isTextual()
                || !"2.0".equals(envelope.path("jsonrpc").asText())) {
            throw new IOException("Invalid MCP JSON-RPC envelope");
        }

        JsonNode id = envelope.get("id");
        if (id == null) {
            if (!envelope.has("method") || !envelope.get("method").isTextual()
                    || envelope.has("result") || envelope.has("error")) {
                throw new IOException("Invalid MCP JSON-RPC notification");
            }
            return;
        }
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0
                || (!envelope.has("result") && !envelope.has("error"))
                || (envelope.has("result") && envelope.has("error"))
                || (envelope.has("error") && !envelope.get("error").isObject())) {
            throw new IOException("Invalid MCP JSON-RPC response envelope");
        }
    }

    private void completeResponse(CompletableFuture<JsonNode> future, JsonNode response) {
        if (response.has("error")) future.completeExceptionally(
                new McpJsonRpcException("MCP JSON-RPC error: " + response.get("error")));
        else future.complete(response.path("result"));
    }

    private void failPending(Throwable failure) {
        pending.forEach((id, future) -> future.completeExceptionally(failure));
        pending.clear();
    }

    private void notification(String method, JsonNode params) throws IOException {
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0"); notification.put("method", method); notification.set("params", params);
        synchronized (lifecycleLock) {
            writeLocked(notification);
        }
    }

    private void writeLocked(JsonNode value) throws IOException {
        byte[] bytes = mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > properties.getMaxFrameSize())
            throw new IOException("MCP frame exceeds configured maximum");
        synchronized (writeLock) {
            if (invalidated || output == null) throw new IOException("MCP client is not active");
            BufferedOutputStream target = output;
            if ("content-length".equals(properties.getTransport())) {
                target.write(("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            }
            target.write(bytes);
            if ("lines".equals(properties.getTransport())) target.write('\n');
            target.flush();
        }
    }

    private JsonNode read() throws IOException {
        if ("content-length".equals(properties.getTransport())) {
            int length = -1; String line;
            while (!(line = readAsciiLine()).isEmpty()) {
                int colon = line.indexOf(':');
                if (colon >= 0 && "content-length".equalsIgnoreCase(line.substring(0, colon).trim()))
                    length = Integer.parseInt(line.substring(colon + 1).trim());
            }
            if (length < 0) throw new IOException("MCP response has no Content-Length");
            if (length > properties.getMaxFrameSize()) throw new IOException("MCP frame exceeds configured maximum");
            byte[] body = input.readNBytes(length);
            if (body.length != length) throw new EOFException("Truncated MCP response");
            return mapper.readTree(body);
        }
        String line = readAsciiLine();
        return mapper.readTree(line);
    }

    private String readAsciiLine() throws IOException {
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        int c;
        while ((c = input.read()) != -1 && c != '\n') {
            if (c != '\r') {
                if (value.size() >= properties.getMaxFrameSize())
                    throw new IOException("MCP frame exceeds configured maximum");
                value.write(c);
            }
        }
        if (c == -1) {
            if (value.size() == 0) throw new EOFException("MCP process closed stdout");
            throw new EOFException("MCP line frame must end with LF");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(value.toByteArray()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("MCP line frame is not valid UTF-8", exception);
        }
    }

    @Override
    public void invalidate() {
        synchronized (lifecycleLock) {
            // Invalidate first; no request may register after this point.
            invalidated = true;
            initialized = false;
            generation++;
            failPending(new IllegalStateException("MCP client was invalidated"));
            earlyResponses.clear();
            cancelled.clear();
            synchronized (writeLock) {
                input = null;
                output = null;
            }
            if (readerExecutor != null) {
                readerExecutor.shutdownNow();
                readerExecutor = null;
            }
            process.invalidate();
        }
    }
}
