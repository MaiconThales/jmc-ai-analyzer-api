package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates and canonicalizes the deliberately provider-neutral AI response contract. */
public final class AiProviderResponseValidator {
    private static final Set<String> ROOT_FIELDS = Set.of("summary", "executiveSummary", "overallSeverity",
            "findings", "recommendations", "possibleCauses", "status");
    private static final Set<String> FINDING_FIELDS = Set.of("category", "title", "detail", "severity");
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO", "UNKNOWN");
    private static final Set<String> STATUSES = Set.of("COMPLETED", "PARTIAL", "FAILED", "UNAVAILABLE", "DISABLED");
    private static final Pattern UNIX_PATH = Pattern.compile("(?<![A-Za-z0-9])/(?:[^\\s/]+/)*[^\\s/]+");
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)\\b[A-Z]:\\\\[^\\s]+|\\\\\\\\[^\\s]+\\\\[^\\s]+");
    private static final Pattern SECRET_QUOTED = Pattern.compile(
            "(?i)(\\b(?:api[_-]?key|token|password|secret)\\b\\s*[:=]\\s*)([\\\"'])(?:[^\\\"']*)\\2");
    private static final Pattern SECRET_UNQUOTED = Pattern.compile(
            "(?i)(\\b(?:api[_-]?key|token|password|secret)\\b\\s*[:=]\\s*)[^\\s,;}\\]]+");
    private static final Pattern TOKEN_QUOTED = Pattern.compile(
            "(?i)(\\btoken\\b\\s+)([\\\"'])(?:[^\\\"']*)\\2");
    private static final Pattern TOKEN_UNQUOTED = Pattern.compile(
            "(?i)(\\btoken\\b\\s+)[^\\s,;}\\]]+");
    private static final Pattern BEARER_VALUE = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");

    private final ObjectMapper mapper;

    public AiProviderResponseValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parses only a JSON object matching the response schema. No prose fallback is permitted.
     * The returned payload is the normalized, redacted canonical JSON.
     */
    public AiInterpretation validate(String raw, String provider, String model, String promptVersion) {
        if (raw == null || Text.utf8Length(raw) > AnalysisLimits.MAX_PROVIDER_PAYLOAD) {
            throw invalid("provider response is missing or exceeds its limit");
        }
        final JsonNode input;
        try {
            input = mapper.reader(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
        } catch (RuntimeException exception) {
            throw invalid("provider response is not valid JSON");
        }
        if (input == null || !input.isObject()) throw invalid("provider response must be a JSON object");
        ObjectNode result = normalizeRoot((ObjectNode) input);
        String payload;
        try {
            payload = mapper.writeValueAsString(result);
        } catch (RuntimeException exception) {
            throw invalid("provider response could not be normalized");
        }
        if (Text.utf8Length(payload) > AnalysisLimits.MAX_PROVIDER_PAYLOAD) {
            throw invalid("normalized response exceeds its UTF-8 byte limit");
        }
        return new AiInterpretation(result.get("status").asText(), provider, model, promptVersion,
                payload, result.get("summary").asText());
    }

    /** Revalidates an interpretation returned by an adapter at the application boundary. */
    public AiInterpretation validate(AiInterpretation interpretation) {
        if (interpretation == null) throw invalid("interpretation is missing");
        if ("DISABLED".equals(interpretation.status())) {
            if (!"{}".equals(interpretation.payload())
                    || !"AI provider is disabled".equals(interpretation.summary())) {
                throw invalid("DISABLED interpretation is not the canonical internal sentinel");
            }
            return interpretation;
        }
        AiInterpretation normalized = validate(interpretation.payload(), interpretation.provider(),
                interpretation.model(), interpretation.promptVersion());
        if (!interpretation.status().equals(normalized.status())) {
            throw invalid("interpretation status does not match its payload status");
        }
        return normalized;
    }

    private ObjectNode normalizeRoot(ObjectNode input) {
        rejectUnknown(input, ROOT_FIELDS, "response");
        boolean hasSummary = input.has("summary");
        boolean hasExecutiveSummary = input.has("executiveSummary");
        if (hasSummary == hasExecutiveSummary) {
            throw invalid("response must contain exactly one of summary or executiveSummary");
        }
        String summary = text(input, hasSummary ? "summary" : "executiveSummary", true);
        String status = enumText(input, "status", STATUSES);
        if ("DISABLED".equals(status)) {
            throw invalid("DISABLED is reserved for the internal sentinel");
        }
        String severity = enumText(input, "overallSeverity", SEVERITIES);
        ObjectNode output = mapper.createObjectNode();
        output.put("summary", summary);
        output.put("overallSeverity", severity);
        output.set("findings", normalizeFindings(required(input, "findings")));
        output.set("recommendations", normalizeTextArray(required(input, "recommendations"), "recommendations"));
        output.set("possibleCauses", normalizeTextArray(required(input, "possibleCauses"), "possibleCauses"));
        output.put("status", status);
        return output;
    }

    private JsonNode required(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) throw invalid(name + " is required");
        return value;
    }

    private ArrayNode normalizeFindings(JsonNode value) {
        if (!value.isArray() || value.size() > AnalysisLimits.MAX_FINDINGS) throw invalid("findings is invalid or too large");
        ArrayNode output = mapper.createArrayNode();
        for (JsonNode finding : value) {
            if (!finding.isObject()) throw invalid("findings must contain objects");
            ObjectNode object = (ObjectNode) finding;
            rejectUnknown(object, FINDING_FIELDS, "finding");
            String title = text(object, "title", true);
            String detail = text(object, "detail", true);
            String findingSeverity = enumText(object, "severity", SEVERITIES);
            ObjectNode normalized = mapper.createObjectNode();
            normalized.put("title", title);
            normalized.put("detail", detail);
            normalized.put("severity", findingSeverity);
            if (object.has("category")) normalized.put("category", text(object, "category", true));
            output.add(normalized);
        }
        return output;
    }

    private ArrayNode normalizeTextArray(JsonNode value, String name) {
        ArrayNode output = mapper.createArrayNode();
        if (value == null || value.isNull()) return output;
        if (!value.isArray() || value.size() > AnalysisLimits.MAX_ITEMS_PER_CATEGORY) throw invalid(name + " is invalid or too large");
        for (JsonNode item : value) {
            if (!item.isTextual()) throw invalid(name + " must contain strings");
            output.add(cleanText(item.asText(), name));
        }
        return output;
    }

    private String text(ObjectNode node, String name, boolean required) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) throw invalid(name + " is required and must be a string");
        String cleaned = cleanText(value.asText(), name);
        if (required && cleaned.isBlank()) throw invalid(name + " must be non-blank");
        return cleaned;
    }

    private String enumText(ObjectNode node, String name, Set<String> allowed) {
        String value = text(node, name, true).toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) throw invalid(name + " is unknown");
        return value;
    }

    private String cleanText(String value, String name) {
        String cleaned = value.trim();
        if (Text.utf8Length(cleaned) > AnalysisLimits.MAX_TEXT) throw invalid(name + " exceeds its UTF-8 byte limit");
        cleaned = SECRET_QUOTED.matcher(cleaned).replaceAll("$1$2[REDACTED]$2");
        cleaned = SECRET_UNQUOTED.matcher(cleaned).replaceAll("$1[REDACTED]");
        cleaned = TOKEN_QUOTED.matcher(cleaned).replaceAll("$1$2[REDACTED]$2");
        cleaned = TOKEN_UNQUOTED.matcher(cleaned).replaceAll("$1[REDACTED]");
        cleaned = BEARER_VALUE.matcher(cleaned).replaceAll("Bearer [REDACTED]");
        cleaned = WINDOWS_PATH.matcher(cleaned).replaceAll("[REDACTED_PATH]");
        cleaned = UNIX_PATH.matcher(cleaned).replaceAll("[REDACTED_PATH]");
        if (Text.utf8Length(cleaned) > AnalysisLimits.MAX_TEXT) {
            throw invalid(name + " exceeds its UTF-8 byte limit after redaction");
        }
        return cleaned;
    }

    private void rejectUnknown(ObjectNode node, Set<String> allowed, String name) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!allowed.contains(entry.getKey())) throw invalid(name + " contains unknown fields");
        }
    }

    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException("Invalid AI provider response: " + message); }
}
