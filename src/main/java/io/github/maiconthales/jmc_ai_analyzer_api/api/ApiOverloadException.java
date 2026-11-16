package io.github.maiconthales.jmc_ai_analyzer_api.api;

/** Signals that the service cannot accept more work at this time. */
public class ApiOverloadException extends RuntimeException {
    public ApiOverloadException() { }
}
