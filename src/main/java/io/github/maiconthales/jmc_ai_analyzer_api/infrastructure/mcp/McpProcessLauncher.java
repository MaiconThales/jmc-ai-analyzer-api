package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface McpProcessLauncher {

    Process start(List<String> command) throws IOException;
}
