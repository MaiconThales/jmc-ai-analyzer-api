package io.github.maiconthales.jmc_ai_analyzer_api.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.maiconthales.jmc_ai_analyzer_api.application.AnalysisApplicationService;
import io.github.maiconthales.jmc_ai_analyzer_api.application.AnalysisQueryService;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AiInterpretation;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AiInterpretationStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.JsonNodeFactory;

class AnalysisControllerTest {
    private final AnalysisApplicationService application = mock(AnalysisApplicationService.class);
    private final AnalysisQueryService query = mock(AnalysisQueryService.class);
    private MockMvc mvc;
    private UUID id;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AnalysisController(application, query))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        id = UUID.randomUUID();
    }

    @Test
    void createsOnlyFromControlledFileIdAndReturnsPending() throws Exception {
        AnalysisSession session = mock(AnalysisSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getStatus()).thenReturn(AnalysisStatus.PENDING);
        when(application.createAndSchedule(any())).thenReturn(session);

        mvc.perform(post("/api/v1/analyses").contentType("application/json")
                        .content("{\"fileId\":\"cmVjb3JkaW5nLmpmcg\",\"topN\":10}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(application).createAndSchedule(any());
    }

    @Test
    void rejectsInvalidUuidAndHistoryBounds() throws Exception {
        mvc.perform(get("/api/v1/analyses/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_UUID"));
        mvc.perform(get("/api/v1/analyses/history").param("page", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/analyses/history").param("size", "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/analyses/history").param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verifyNoInteractions(query);
    }

    @Test
    void doesNotExposeInternalPathOrRawSessionObject() throws Exception {
        var session = mock(AnalysisSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getFileId()).thenReturn("cmVjb3JkaW5nLmpmcg");
        when(session.getFileName()).thenReturn("recording.jfr");
        when(session.getStatus()).thenReturn(AnalysisStatus.PENDING);
        when(session.getCreatedAt()).thenReturn(Instant.now());
        when(query.get(id)).thenReturn(new AnalysisQueryService.Snapshot(session, List.of(), List.of(), List.of()));

        mvc.perform(get("/api/v1/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.fileName").value("recording.jfr"))
                .andExpect(jsonPath("$.session.path").doesNotExist())
                .andExpect(jsonPath("$.session.rawJfr").doesNotExist());
    }

    @Test
    void redactsSensitivePayloadFieldsOnDetailHistoryAndInterpretation() throws Exception {
        AnalysisSession session = new AnalysisSession("cmVjb3JkaW5nLmpmcg", "recording.jfr", 123L, Instant.now());
        ObjectNode sensitive = JsonNodeFactory.instance.objectNode();
        sensitive.put("summary", "safe");
        sensitive.put("path", "C:\\private\\recording.jfr");
        sensitive.put("filePath", "/private/recording.jfr");
        sensitive.put("nested_secret", "do-not-return");
        sensitive.put("token", "bearer-secret");
        sensitive.put("rawJfr", "raw-recording-bytes");
        sensitive.put("bytes", "recording-content");
        ObjectNode nested = sensitive.putObject("details");
        nested.put("password", "password-value");
        nested.put("apiKey", "api-key-value");

        AnalysisResult result = new AnalysisResult("cpu_hot_methods", sensitive);
        AiInterpretation interpretation = new AiInterpretation(session, AiInterpretationStatus.COMPLETED,
                "provider", "model", "prompt-v1", "safe summary", "LOW", sensitive, null, null);
        when(query.get(id)).thenReturn(new AnalysisQueryService.Snapshot(
                session, List.of(), List.of(result), List.of(interpretation)));
        when(query.history(any())).thenReturn(new PageImpl<>(List.of(
                new AnalysisQueryService.Snapshot(session, List.of(), List.of(result), List.of(interpretation)))));

        String detail = mvc.perform(get("/api/v1/analyses/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String history = mvc.perform(get("/api/v1/analyses/history"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String interpretationResponse = mvc.perform(get("/api/v1/analyses/{id}/interpretation", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String response : List.of(detail, history, interpretationResponse)) {
            org.junit.jupiter.api.Assertions.assertTrue(response.contains("safe"));
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("private"));
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("do-not-return"));
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("bearer-secret"));
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("raw-recording-bytes"));
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("recording-content"));
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("password-value"));
            org.junit.jupiter.api.Assertions.assertFalse(response.contains("api-key-value"));
        }
    }

    @Test
    void canonicalizesSensitiveFieldNamesAndRedactsSensitiveTextValues() throws Exception {
        AnalysisSession session = new AnalysisSession("opaque-id", "recording.jfr", 1L, Instant.now());
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("raw_jfr", "raw-recording");
        payload.put("rawBytes", "recording-bytes");
        payload.put("jfrPath", "C:\\private\\recording.jfr");
        payload.put("api-key", "api-key-value");
        payload.put("authorization", "Bearer bearer-value");
        payload.put("credential", "credential-value");
        payload.put("privateKey", "private-key-value");
        payload.put("safeNote", "read C:\\private\\x.jfr /private/y.jfr "
                + "Bearer bearer-secret token=token-value password='password-value' "
                + "secret:secret-value apiKey=api-value");

        AnalysisResult result = new AnalysisResult("rules", payload);
        when(query.get(id)).thenReturn(new AnalysisQueryService.Snapshot(
                session, List.of(), List.of(result), List.of()));

        String response = mvc.perform(get("/api/v1/analyses/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String secret : List.of("raw-recording", "recording-bytes", "private\\\\recording.jfr",
                "api-key-value", "bearer-value", "credential-value", "private-key-value",
                "bearer-secret", "token-value", "password-value", "secret-value", "api-value",
                "private/y.jfr")) {
            org.junit.jupiter.api.Assertions.assertFalse(response.contains(secret), secret);
        }
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("[REDACTED_PATH]"));
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("[REDACTED]"));
    }
}
