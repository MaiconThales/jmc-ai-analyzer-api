package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

/**
 * A JSON-RPC error returned by the peer for one request.
 *
 * <p>This is request-local: it must not invalidate the shared MCP transport.
 * Framing, EOF, decoding, and other transport/protocol failures are represented
 * by their original exception and may invalidate the shared transport.</p>
 */
public final class McpJsonRpcException extends IllegalStateException {
    public McpJsonRpcException(String message) {
        super(message);
    }
}
