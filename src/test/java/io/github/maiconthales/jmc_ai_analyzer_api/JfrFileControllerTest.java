package io.github.maiconthales.jmc_ai_analyzer_api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import io.github.maiconthales.jmc_ai_analyzer_api.api.JfrFileController;
import io.github.maiconthales.jmc_ai_analyzer_api.api.ApiExceptionHandler;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileCatalogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JfrFileControllerTest {

    @TempDir
    Path tempDirectory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new JfrFileController(new JfrFileCatalogService(tempDirectory, 10)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsControlledMetadataWithoutPath() throws Exception {
        Files.writeString(tempDirectory.resolve("recording.jfr"), "content");

        mockMvc.perform(get("/api/v1/jfr-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("recording.jfr".getBytes())))
                .andExpect(jsonPath("$[0].name").value("recording.jfr"))
                .andExpect(jsonPath("$[0].size").value(7))
                .andExpect(jsonPath("$[0].modifiedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].path").doesNotExist());
    }

    @Test
    void returnsBadRequestForTraversalAndNotFoundForMissingFile() throws Exception {
        mockMvc.perform(get("/api/v1/jfr-files/{id}", ".."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        mockMvc.perform(get("/api/v1/jfr-files/{id}", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("missing.jfr".getBytes())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
