package io.github.maiconthales.jmc_ai_analyzer_api.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.maiconthales.jmc_ai_analyzer_api.application.AnalysisQueryService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.InvalidJfrFileIdException;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void mapsAllErrorsToSafeJsonEnvelope() throws Exception {
        assertError("validation", 400, "VALIDATION_ERROR");
        assertError("uuid", 400, "INVALID_UUID");
        assertError("file", 400, "INVALID_FILE");
        assertError("file-not-found", 404, "FILE_NOT_FOUND");
        assertError("analysis-not-found", 404, "ANALYSIS_NOT_FOUND");
        assertError("bad", 400, "BAD_REQUEST");
        assertError("conflict", 409, "CONFLICT");
        assertError("overload", 429, "OVERLOAD");
        assertError("unexpected", 500, "UNEXPECTED_ERROR");
    }

    @Test
    void acceptsOnlySafeCorrelationIdAndDoesNotLeakDetails() throws Exception {
        String body = mvc.perform(get("/error/unexpected").header("X-Correlation-Id", "trace-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.correlationId").value("trace-123"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("secret") || body.contains("java.lang"));
    }

    private void assertError(String endpoint, int status, String code) throws Exception {
        mvc.perform(get("/error/" + endpoint))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @RestController
    static class ErrorController {
        @GetMapping("/error/validation") void validation() { throw new org.springframework.http.converter.HttpMessageNotReadableException("invalid payload", (org.springframework.http.HttpInputMessage) null); }
        @GetMapping("/error/uuid") void uuid() { throw new InvalidUuidException(); }
        @GetMapping("/error/file") void file() { throw new InvalidJfrFileIdException("secret/path"); }
        @GetMapping("/error/file-not-found") void fileNotFound() { throw new JfrFileNotFoundException("secret/path"); }
        @GetMapping("/error/analysis-not-found") void analysisNotFound() { throw new AnalysisQueryService.AnalysisNotFoundException(UUID.randomUUID()); }
        @GetMapping("/error/bad") void bad() { throw new IllegalArgumentException("secret command"); }
        @GetMapping("/error/conflict") void conflict() { throw new DataIntegrityViolationException("secret"); }
        @GetMapping("/error/overload") void overload() { throw new RejectedExecutionException("secret"); }
        @GetMapping("/error/unexpected") void unexpected() { throw new RuntimeException("secret"); }
    }
}
