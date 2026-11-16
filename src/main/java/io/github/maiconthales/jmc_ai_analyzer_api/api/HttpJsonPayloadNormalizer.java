package io.github.maiconthales.jmc_ai_analyzer_api.api;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Defensive copy/sanitizer for JSON crossing the HTTP boundary.
 *
 * <p>The persistence model deliberately keeps structured JSON, but that JSON
 * is not an HTTP contract. This class therefore never returns a node owned by
 * the persistence layer and applies conservative limits before returning it.</p>
 */
final class HttpJsonPayloadNormalizer {
    private static final String REDACTED = "[REDACTED]";
    private static final String LIMIT = "[REDACTED_LIMIT]";
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 2_000;
    private static final int MAX_STRING_CHARS = 4_096;
    private static final int MAX_UTF8_BYTES = 32_000;
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)(?:\\b[A-Z]:\\\\[^\\s]+|\\\\\\\\[^\\s]+(?:\\\\[^\\s]+)+)");
    private static final Pattern UNIX_PATH = Pattern.compile(
            "(?<![A-Za-z0-9])/(?:[^\\s/]+/)*[^\\s/]+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(\\bBearer\\s+)[^\\s,;}\\]]+");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:token|password|secret|api[_-]?key)\\b\\s*[:=]\\s*)([\\\"']?)[^\\s,;}\\]]+\\2");

    JsonNode normalize(JsonNode source) {
        if (source == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        Counter counter = new Counter();
        JsonNode copy = copy(source, 0, counter);
        if (utf8Size(copy) > MAX_UTF8_BYTES) {
            return marker("payload exceeds HTTP safety limit");
        }
        return copy;
    }

    /**
     * Sanitizes scalar HTTP fields which are not part of a JSON payload (for
     * example an MCP error message or an AI summary).  Those fields must not
     * bypass the same boundary policy used for payload text.
     */
    String normalizeText(String source) {
        if (source == null) {
            return null;
        }
        String value = sanitizeText(source);
        if (value.length() > MAX_STRING_CHARS) {
            value = value.substring(0, MAX_STRING_CHARS) + LIMIT;
        }
        return value;
    }

    private JsonNode copy(JsonNode source, int depth, Counter counter) {
        if (depth > MAX_DEPTH || ++counter.nodes > MAX_NODES) {
            return JsonNodeFactory.instance.textNode(LIMIT);
        }
        if (source.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = source.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (sensitiveKey(field.getKey())) {
                    continue;
                }
                result.set(field.getKey(), copy(field.getValue(), depth + 1, counter));
            }
            return result;
        }
        if (source.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            int count = 0;
            for (JsonNode child : source) {
                if (count++ >= MAX_NODES) {
                    result.add(LIMIT);
                    break;
                }
                result.add(copy(child, depth + 1, counter));
            }
            return result;
        }
        if (source.isTextual()) {
            String value = sanitizeText(source.textValue());
            if (value.length() > MAX_STRING_CHARS) {
                return JsonNodeFactory.instance.textNode(value.substring(0, MAX_STRING_CHARS) + LIMIT);
            }
            return JsonNodeFactory.instance.textNode(value);
        }
        // Scalars are copied by value; the source node is never returned.
        if (source.isBoolean()) return JsonNodeFactory.instance.booleanNode(source.booleanValue());
        if (source.isNumber()) return source.deepCopy();
        return JsonNodeFactory.instance.nullNode();
    }

    private static boolean sensitiveKey(String key) {
        // Treat raw_jfr, raw-jfr, rawJfr and "raw jfr" identically.
        // Canonicalize all punctuation, not just the common separators. This
        // makes e.g. raw_jfr, raw-jfr, raw.jfr and raw jfr equivalent.
        String normalized = key == null ? ""
                : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.contains("path")
                || normalized.contains("file")
                || normalized.contains("jfr")
                || normalized.contains("raw")
                || normalized.contains("bytes")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.contains("authorization")
                || normalized.contains("bearer")
                || normalized.contains("credential")
                || normalized.contains("privatekey");
    }

    private static String sanitizeText(String value) {
        String sanitized = SECRET_ASSIGNMENT.matcher(value).replaceAll("$1[REDACTED]");
        sanitized = BEARER.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = WINDOWS_PATH.matcher(sanitized).replaceAll("[REDACTED_PATH]");
        return UNIX_PATH.matcher(sanitized).replaceAll("[REDACTED_PATH]");
    }

    private static int utf8Size(JsonNode node) {
        return node.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static ObjectNode marker(String reason) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("_redacted", true);
        result.put("reason", reason);
        return result;
    }

    private static final class Counter {
        private int nodes;
    }
}
