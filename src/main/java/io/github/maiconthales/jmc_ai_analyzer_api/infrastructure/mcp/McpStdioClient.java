package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import java.io.InputStream;
import java.io.OutputStream;

public interface McpStdioClient extends AutoCloseable {

    void start();

    boolean isRunning();

    OutputStream stdin();

    InputStream stdout();

    InputStream stderr();

    @Override
    void close();

    /** Closing is also the safe invalidation operation for a late STDIO response. */
    default void invalidate() {
        close();
    }
}
