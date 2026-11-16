package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem;

/** Raised when a controlled file identifier cannot be resolved in the JFR directory. */
public class JfrFileNotFoundException extends RuntimeException {

    public JfrFileNotFoundException(String id) {
        super("JFR file not found: " + id);
    }
}
