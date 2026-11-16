package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class McpProtocolClientConfigurationTest {

    @Test
    void enabledProtocolFailureInvalidatesLazilyStartedProcess() {
        McpClientProperties properties = new McpClientProperties();
        properties.setEnabled(true);
        AtomicBoolean running = new AtomicBoolean();
        AtomicBoolean invalidated = new AtomicBoolean();
        McpStdioClient stdio = new McpStdioClient() {
            @Override public void start() { running.set(true); }
            @Override public boolean isRunning() { return running.get(); }
            @Override public java.io.OutputStream stdin() { return new ByteArrayOutputStream(); }
            @Override public java.io.InputStream stdout() { return new ByteArrayInputStream(new byte[0]); }
            @Override public java.io.InputStream stderr() { return new ByteArrayInputStream(new byte[0]); }
            @Override public void close() { running.set(false); invalidated.set(true); }
        };

        McpProtocolClient client = new McpProtocolClientConfiguration()
                .mcpProtocolClient(stdio, properties);

        assertThrows(IllegalStateException.class,
                () -> client.callTool("tool", tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()));
        assertTrue(invalidated.get());
        assertFalse(running.get());
    }

    @Test
    void disabledClientDoesNotStartProcess() {
        McpClientProperties properties = new McpClientProperties();
        AtomicBoolean started = new AtomicBoolean();
        McpStdioClient stdio = new McpStdioClient() {
            @Override public void start() { started.set(true); }
            @Override public boolean isRunning() { return false; }
            @Override public java.io.OutputStream stdin() { return new ByteArrayOutputStream(); }
            @Override public java.io.InputStream stdout() { return new ByteArrayInputStream(new byte[0]); }
            @Override public java.io.InputStream stderr() { return new ByteArrayInputStream(new byte[0]); }
            @Override public void close() { }
        };

        McpProtocolClient client = new McpProtocolClientConfiguration()
                .mcpProtocolClient(stdio, properties);
        assertThrows(IllegalStateException.class,
                () -> client.callTool("tool", tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()));
        assertFalse(started.get());
    }
}
