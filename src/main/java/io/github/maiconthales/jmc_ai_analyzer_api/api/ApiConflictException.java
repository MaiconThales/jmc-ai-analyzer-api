package io.github.maiconthales.jmc_ai_analyzer_api.api;

/** Signals a request that conflicts with the current resource state. */
public class ApiConflictException extends RuntimeException {
    public ApiConflictException() { }
}
