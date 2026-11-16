package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

/** Limits applied before a consolidated result can cross the AI boundary. */
public final class AnalysisLimits {
    public static final int MAX_FINDINGS = 100;
    public static final int MAX_ITEMS_PER_CATEGORY = 50;
    public static final int MAX_TEXT = 2_000;
    public static final int MAX_ERRORS = 20;
    /** Prompt limit is measured in serialized UTF-8 bytes, like provider payloads. */
    public static final int MAX_PROMPT_BYTES = 32_000;
    public static final int MAX_PROVIDER_PAYLOAD = 32_000;

    private AnalysisLimits() { }
}
