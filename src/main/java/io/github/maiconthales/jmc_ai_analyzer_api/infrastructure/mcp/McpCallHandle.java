package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import tools.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A cancellable MCP request and the resources owned by that request.
 *
 * <p>Cleanup callbacks run synchronously on the caller that cancels/closes the
 * handle. Implementations must therefore make them bounded or non-blocking
 * (or hand off blocking process teardown to a bounded lifecycle executor).
 * The STDIO bridge uses bounded process teardown. Cleanup is guarded and is
 * invoked at most once, even when cancellation races with normal close.</p>
 */
public final class McpCallHandle implements AutoCloseable {
    private final Future<JsonNode> future;
    private final Runnable closeCleanup;
    private final Runnable cancelCleanup;
    private final AtomicBoolean closed = new AtomicBoolean();

    public McpCallHandle(Future<JsonNode> future, Runnable cleanup) {
        this(future, cleanup, cleanup);
    }

    public McpCallHandle(Future<JsonNode> future, Runnable closeCleanup, Runnable cancelCleanup) {
        this.future = Objects.requireNonNull(future, "future");
        this.closeCleanup = Objects.requireNonNull(closeCleanup, "closeCleanup");
        this.cancelCleanup = Objects.requireNonNull(cancelCleanup, "cancelCleanup");
    }

    public static McpCallHandle completed(JsonNode payload, Runnable cleanup) {
        return new McpCallHandle(CompletableFuture.completedFuture(payload), cleanup);
    }

    public Future<JsonNode> future() {
        return future;
    }

    /** Cancels the protocol request before releasing its connection/session. */
    public void cancel() {
        future.cancel(true);
        if (closed.compareAndSet(false, true)) {
            cancelCleanup.run();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeCleanup.run();
        }
    }
}
