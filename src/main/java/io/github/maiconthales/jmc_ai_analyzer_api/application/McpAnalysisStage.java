package io.github.maiconthales.jmc_ai_analyzer_api.application;

/** The five deterministic MCP analyses executed for one JFR recording. */
public enum McpAnalysisStage {
    AUTOMATED_RULES("automated_rules"),
    CPU_HOT_METHODS("cpu_hot_methods"),
    GC_AND_MEMORY("gc_and_memory"),
    LOCK_CONTENTIONS("lock_contentions"),
    FREQUENT_EXCEPTIONS("frequent_exceptions");

    private final String resultType;

    McpAnalysisStage(String resultType) {
        this.resultType = resultType;
    }

    /** Stable, allow-listed value stored in analysis_result.result_type. */
    public String resultType() {
        return resultType;
    }
}
