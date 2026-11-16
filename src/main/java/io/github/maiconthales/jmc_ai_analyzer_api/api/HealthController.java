package io.github.maiconthales.jmc_ai_analyzer_api.api;

import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the liveness state of the HTTP application.
 *
 * <p>Dependency checks will be added with the corresponding infrastructure
 * tasks. Keeping this endpoint independent of the database makes it useful
 * while the application is starting and during local development.</p>
 */
@RestController
@RequestMapping(path = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", Instant.now());
    }

    public record HealthResponse(String status, Instant timestamp) {
    }
}
