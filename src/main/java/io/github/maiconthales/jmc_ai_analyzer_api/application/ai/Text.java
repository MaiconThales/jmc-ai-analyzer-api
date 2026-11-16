package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.util.List;
import java.util.Objects;
import java.nio.charset.StandardCharsets;

final class Text {
    private Text() { }
    static String required(String value, String name) {
        if (value == null || value.isBlank() || utf8Length(value) > AnalysisLimits.MAX_TEXT) {
            throw new IllegalArgumentException(name + " must be non-blank and at most " + AnalysisLimits.MAX_TEXT + " UTF-8 bytes");
        }
        return value;
    }
    static String bounded(String value, String name) { return required(value, name); }
    static String providerPayload(String value, String name) {
        if (value == null || value.isBlank() || utf8Length(value) > AnalysisLimits.MAX_PROVIDER_PAYLOAD) {
            throw new IllegalArgumentException(name + " must be non-blank and at most "
                    + AnalysisLimits.MAX_PROVIDER_PAYLOAD + " UTF-8 bytes");
        }
        return value;
    }

    static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
    static <T> List<T> list(List<T> value, int max, String name) {
        Objects.requireNonNull(value, name);
        if (value.size() > max || value.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " exceeds its limit or contains null");
        }
        return List.copyOf(value);
    }
}
