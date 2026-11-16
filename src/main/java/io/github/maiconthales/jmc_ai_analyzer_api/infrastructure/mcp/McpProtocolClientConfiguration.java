package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the MCP protocol boundary without starting a child process during
 * application bootstrap. The actual STDIO process is started lazily by the
 * protocol implementation when an MCP call is made.
 */
@Configuration
public class McpProtocolClientConfiguration {

    @Bean
    public McpProtocolClient mcpProtocolClient(McpStdioClient stdioClient,
                                               McpClientProperties properties) {
        return new McpStdioJsonRpcClient(stdioClient, properties);
    }
}
