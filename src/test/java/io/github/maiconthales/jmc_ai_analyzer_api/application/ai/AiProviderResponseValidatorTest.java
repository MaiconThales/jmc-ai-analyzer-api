package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderResponseValidatorTest {
    private final AiProviderResponseValidator validator = new AiProviderResponseValidator(new ObjectMapper());

    @Test
    void acceptsAndNormalizesStructuredResponseWhilePreservingMetadata() {
        AiInterpretation result = validator.validate("""
                {"executiveSummary":"  tudo certo  ","overallSeverity":"low","findings":[],
                 "recommendations":[],"possibleCauses":[],"status":"completed"}
                """, "provider-x", "model-y", "prompt-v2");

        assertEquals("tudo certo", result.summary());
        assertEquals("provider-x", result.provider());
        assertEquals("model-y", result.model());
        assertEquals("prompt-v2", result.promptVersion());
        assertTrue(result.payload().contains("\"recommendations\":[]"));
    }

    @Test
    void acceptsNormalizedPayloadBetweenTextAndProviderLimits() {
        String recommendations = "[\"" + "r".repeat(1_500) + "\",\"" + "s".repeat(1_500) + "\"]";
        AiInterpretation result = validator.validate(
                "{\"summary\":\"ok\",\"overallSeverity\":\"LOW\",\"findings\":[],"
                        + "\"recommendations\":" + recommendations
                        + ",\"possibleCauses\":[],\"status\":\"COMPLETED\"}", "p", "m", "v");

        assertTrue(result.payload().length() > AnalysisLimits.MAX_TEXT);
        assertTrue(result.payload().length() <= AnalysisLimits.MAX_PROVIDER_PAYLOAD);
    }

    @Test
    void appliesProviderLimitToUtf8BytesNotJavaCharacterCount() {
        String emoji = "😀";
        String valid = "{" + "\"summary\":\"" + emoji.repeat(7_900)
                + "\",\"overallSeverity\":\"LOW\",\"findings\":[],"
                + "\"recommendations\":[],\"possibleCauses\":[],\"status\":\"COMPLETED\"}";
        assertTrue(valid.length() < AnalysisLimits.MAX_PROVIDER_PAYLOAD);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(valid, "p", "m", "v"));

        String text = "😀".repeat(AnalysisLimits.MAX_TEXT / 4 + 1);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"" + text + "\",\"overallSeverity\":\"LOW\","
                        + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],"
                        + "\"status\":\"COMPLETED\"}", "p", "m", "v"));
    }

    @Test
    void requiresExactlyOneSummaryAndAllStructuredCollections() {
        String complete = "\"summary\":\"ok\",\"overallSeverity\":\"low\","
                + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],\"status\":\"completed\"";
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"ok\",\"executiveSummary\":\"also\",\"overallSeverity\":\"low\","
                        + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],\"status\":\"completed\"}", "p", "m", "v"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"overallSeverity\":\"low\",\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],\"status\":\"completed\"}", "p", "m", "v"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"ok\",\"overallSeverity\":\"low\",\"status\":\"completed\"}", "p", "m", "v"));
        assertEquals("ok", validator.validate("{" + complete + "}", "p", "m", "v").summary());
    }

    @Test
    void rejectsDuplicateKeysAndTrailingJson() {
        String response = "{\"summary\":\"ok\",\"overallSeverity\":\"low\",\"findings\":[],"
                + "\"recommendations\":[],\"possibleCauses\":[],\"status\":\"completed\"}";
        assertThrows(IllegalArgumentException.class, () -> validator.validate(response.replace("\"summary\":\"ok\"", "\"summary\":\"ok\",\"summary\":\"again\""), "p", "m", "v"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(response + " {}", "p", "m", "v"));
    }

    @Test
    void rejectsMalformedScalarMissingFieldsAndUnknownEnums() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("plain text", "p", "m", "v"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("{}", "p", "m", "v"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"x\",\"overallSeverity\":\"fatal\",\"status\":\"completed\"}", "p", "m", "v"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"x\",\"overallSeverity\":\"low\",\"status\":\"other\"}", "p", "m", "v"));
    }

    @Test
    void rejectsExcessiveCollectionsAndPayload() {
        String item = "\"recommendation\"";
        String many = "[" + (item + ",").repeat(AnalysisLimits.MAX_ITEMS_PER_CATEGORY) + item + "]";
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"x\",\"overallSeverity\":\"low\",\"recommendations\":" + many + ",\"status\":\"completed\"}", "p", "m", "v"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("x".repeat(AnalysisLimits.MAX_PROVIDER_PAYLOAD + 1), "p", "m", "v"));
    }

    @Test
    void redactsPathsAndSecretsBeforeReturningPayload() {
        AiInterpretation result = validator.validate("""
                {"summary":"read C:\\\\Users\\\\alice\\\\secret.txt token=abc123 apiKey:\\\"key-value\\\" password:'password-value' secret:secret-value Bearer bearer-value","overallSeverity":"low",
                 "findings":[],"recommendations":[],"possibleCauses":[],"status":"completed"}
                """, "provider", "model", "version");

        assertTrue(result.payload().contains("[REDACTED_PATH]"));
        assertTrue(result.payload().contains("token=[REDACTED]"));
        assertTrue(!result.payload().contains("abc123"));
        assertTrue(!result.payload().contains("key-value"));
        assertTrue(!result.payload().contains("password-value"));
        assertTrue(!result.payload().contains("secret-value"));
        assertTrue(!result.payload().contains("bearer-value"));

        AiInterpretation simplePath = validator.validate("""
                {"summary":"read /secret","overallSeverity":"low","findings":[],
                 "recommendations":[],"possibleCauses":[],"status":"completed"}
                """, "provider", "model", "version");
        assertTrue(simplePath.payload().contains("[REDACTED_PATH]"));
    }

    @Test
    void enforcesTextLimitAfterRedactionExpansion() {
        String path = "/x ".repeat(126);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"" + path + "\",\"overallSeverity\":\"LOW\","
                        + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],"
                        + "\"status\":\"COMPLETED\"}", "p", "m", "v"));
    }

    @Test
    void acceptsOnlyCanonicalDisabledSentinel() {
        AiInterpretation sentinel = new AiInterpretation("DISABLED", "provider", "model", "version",
                "{}", "AI provider is disabled");
        assertEquals("DISABLED", validator.validate(sentinel).status());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                new AiInterpretation("DISABLED", "provider", "model", "version", "{\"bad\":true}", "disabled")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "{\"summary\":\"x\",\"overallSeverity\":\"low\",\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],\"status\":\"DISABLED\"}", "p", "m", "v"));
    }

    @Test
    void rejectsExternalStatusThatDiffersFromPayloadStatus() {
        AiInterpretation mismatch = new AiInterpretation("FAILED", "provider", "model", "version",
                "{\"summary\":\"ok\",\"overallSeverity\":\"LOW\",\"findings\":[],"
                        + "\"recommendations\":[],\"possibleCauses\":[],\"status\":\"COMPLETED\"}",
                "ok");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(mismatch));
    }
}
