package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration used only by the MCP process bridge.
 *
 * <p>The command and arguments come from application configuration, never from
 * an HTTP request. ProcessBuilder receives a list and therefore no shell is
 * involved.</p>
 */
@ConfigurationProperties(prefix = "mcp")
public class McpClientProperties {

    private String command = "java";
    private List<String> arguments = new ArrayList<>();
    private Duration startupTimeout = Duration.ofSeconds(5);
    private boolean enabled;
    /**
     * STDIO framing. {@code lines} is the supported default. {@code content-length}
     * is retained only for legacy MCP servers and must not be used for a server
     * that emits line-delimited JSON; the two framings cannot be mixed.
     */
    private String transport = "lines";
    private int maxFrameSize = 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTransport() { return transport; }

    public void setTransport(String transport) {
        String value = transport == null ? "lines" : transport.trim().toLowerCase();
        if (!"lines".equals(value) && !"content-length".equals(value)) {
            throw new IllegalArgumentException("mcp.transport must be 'lines' or 'content-length'");
        }
        this.transport = value;
    }

    public int getMaxFrameSize() { return maxFrameSize; }

    public void setMaxFrameSize(int maxFrameSize) {
        if (maxFrameSize <= 0) throw new IllegalArgumentException("mcp.max-frame-size must be positive");
        this.maxFrameSize = maxFrameSize;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArguments() {
        return List.copyOf(arguments);
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments == null ? new ArrayList<>() : new ArrayList<>(arguments);
    }

    public Duration getStartupTimeout() {
        return startupTimeout;
    }

    public void setStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
    }
}
