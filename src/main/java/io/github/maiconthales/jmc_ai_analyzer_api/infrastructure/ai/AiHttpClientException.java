package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.ai;

/** Technical failure from the provider client. The policy wrapper maps it to a safe result. */
public class AiHttpClientException extends RuntimeException {
    public enum Kind { UNAVAILABLE, FAILED }

    private final Kind kind;

    public AiHttpClientException(String message) { this(message, Kind.UNAVAILABLE); }
    public AiHttpClientException(String message, Throwable cause) { this(message, Kind.UNAVAILABLE, cause); }

    public AiHttpClientException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public AiHttpClientException(String message, Kind kind, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind == null ? Kind.UNAVAILABLE : kind; }
}
