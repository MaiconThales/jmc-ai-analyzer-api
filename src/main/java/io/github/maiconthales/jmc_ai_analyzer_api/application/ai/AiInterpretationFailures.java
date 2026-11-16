package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

/** Creates the small, provider-neutral failure contract used when AI is optional. */
public final class AiInterpretationFailures {
    private AiInterpretationFailures() { }

    public static AiInterpretation unavailable(String provider, String model, String promptVersion) {
        return create("UNAVAILABLE", provider, model, promptVersion,
                "AI_UNAVAILABLE", "AI provider is unavailable");
    }

    public static AiInterpretation failed(String provider, String model, String promptVersion) {
        return create("FAILED", provider, model, promptVersion,
                "AI_FAILED", "AI provider could not produce a valid interpretation");
    }

    private static AiInterpretation create(String status, String provider, String model,
                                           String promptVersion, String code, String message) {
        String safeProvider = safe(provider, "provider-neutral");
        String safeModel = safe(model, "unknown");
        String safeVersion = safe(promptVersion, PromptBuilder.DEFAULT_VERSION);
        String payload = "{\"summary\":\"" + code + "\",\"overallSeverity\":\"UNKNOWN\","
                + "\"findings\":[],\"recommendations\":[],\"possibleCauses\":[],"
                + "\"status\":\"" + status + "\"}";
        return new AiInterpretation(status, safeProvider, safeModel, safeVersion, payload, message);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
