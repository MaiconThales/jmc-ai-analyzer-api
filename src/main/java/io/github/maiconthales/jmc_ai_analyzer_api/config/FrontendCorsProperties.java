package io.github.maiconthales.jmc_ai_analyzer_api.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;

/** CORS origins exposed to the browser-based frontend only. */
@Validated
@ConfigurationProperties(prefix = "frontend")
public class FrontendCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @PostConstruct
    void validate() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("frontend.allowed-origins must contain at least one origin");
        }
        for (String origin : allowedOrigins) {
            validateOrigin(origin);
        }
    }

    private static void validateOrigin(String origin) {
        if (origin == null || origin.isBlank() || origin.contains("*")) {
            throw new IllegalStateException("frontend.allowed-origins must contain explicit, non-wildcard origins");
        }

        final URI parsed;
        try {
            parsed = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid frontend CORS origin: " + origin, exception);
        }

        if (!("http".equalsIgnoreCase(parsed.getScheme())
                || "https".equalsIgnoreCase(parsed.getScheme()))
                || parsed.getHost() == null
                || parsed.getRawUserInfo() != null
                || parsed.getRawPath() != null && !parsed.getRawPath().isEmpty()
                || parsed.getRawQuery() != null
                || parsed.getRawFragment() != null) {
            throw new IllegalStateException("Invalid frontend CORS origin: " + origin);
        }
    }
}
