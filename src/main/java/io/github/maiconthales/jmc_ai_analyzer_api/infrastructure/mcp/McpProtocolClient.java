package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import tools.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

/**
 * Minimal protocol boundary used by the typed MCP adapter.
 *
 * <p>The protocol/stdio implementation is deliberately injected. This keeps
 * the adapter independent of an MCP SDK while still making the tool name and
 * arguments explicit at its boundary.</p>
 */
@FunctionalInterface
public interface McpProtocolClient {

    /** Calls one already-authorized MCP tool and returns its structured payload. */
    JsonNode callTool(String toolName, JsonNode arguments);

    /**
     * Starts a request. Cancellation is request-scoped; it must not invalidate
     * a shared session. Existing synchronous test doubles remain source compatible.
     */
    default McpCallHandle callToolAsync(String toolName, JsonNode arguments) {
        var future = CompletableFuture.supplyAsync(() -> callTool(toolName, arguments));
        return new McpCallHandle(future, () -> { }, this::cancelRequest);
    }

    /** Cancels one request without closing the shared protocol session. */
    default void cancelRequest() {
        // Synchronous clients have no request registration to remove.
    }

    /** Invalidates a session that may have a late response in flight. */
    default void invalidate() {
        // Synchronous clients have no session resource to invalidate.
    }
}
