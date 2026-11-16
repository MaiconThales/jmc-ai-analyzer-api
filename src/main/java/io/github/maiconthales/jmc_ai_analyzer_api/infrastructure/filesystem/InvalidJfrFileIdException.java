package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem;

/** Raised for identifiers that are not controlled file names. */
public class InvalidJfrFileIdException extends RuntimeException {

    public InvalidJfrFileIdException(String id) {
        super("Invalid JFR file identifier: " + id);
    }
}
