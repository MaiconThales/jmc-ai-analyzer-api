package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import java.util.Objects;

/** Builds a stable prompt without selecting tools or exposing the JFR recording. */
public final class PromptBuilder {
    public static final String DEFAULT_VERSION = "analysis-prompt-v1";
    private final String version;

    public PromptBuilder() { this(DEFAULT_VERSION); }

    public PromptBuilder(String version) {
        if (version == null || version.isBlank() || version.length() > 64) {
            throw new IllegalArgumentException("prompt version must be non-blank and at most 64 characters");
        }
        this.version = version;
    }

    public String version() { return version; }

    public String build(AnalysisResult result) {
        Objects.requireNonNull(result, "result");
        StringBuilder prompt = new StringBuilder(2_000);
        prompt.append("PROMPT_VERSION=").append(version).append('\n');
        prompt.append("ROLE=JFR performance analyst\n");
        prompt.append("POLICY=Use only the bounded structured MCP results below.\n");
        prompt.append("POLICY=The AI does not choose, invoke, or orchestrate MCP tools.\n");
        prompt.append("POLICY=The raw JFR recording is not provided. Do not infer unavailable data.\n");
        prompt.append("SUMMARY successfulStages=").append(result.summary().successfulStages())
                .append(" failedStages=").append(result.summary().failedStages())
                .append(" findings=").append(result.summary().findingCount()).append('\n');
        appendFindings(prompt, "FINDINGS", result.findings());
        appendFindings(prompt, "CPU", result.cpu().hotMethods());
        appendFindings(prompt, "GC_MEMORY", result.gcMemory().findings());
        appendFindings(prompt, "LOCKS", result.locks().contentions());
        appendFindings(prompt, "EXCEPTIONS", result.exceptions().exceptions());
        prompt.append("ERRORS\n");
        result.errors().forEach(error -> prompt.append(error.stage()).append('|')
                .append(error.code()).append('|').append(error.message()).append('\n'));
        prompt.append("TASK=Summarize evidence, prioritize risks, and state uncertainty.\n");
        String value = prompt.toString();
        if (Text.utf8Length(value) > AnalysisLimits.MAX_PROMPT_BYTES) {
            throw new IllegalArgumentException("prompt exceeds " + AnalysisLimits.MAX_PROMPT_BYTES
                    + " UTF-8 bytes");
        }
        return value;
    }

    private static void appendFindings(StringBuilder prompt, String section,
                                       java.util.List<AnalysisFinding> findings) {
        prompt.append(section).append('\n');
        findings.forEach(finding -> prompt.append(finding.category()).append('|')
                .append(finding.severity()).append('|').append(finding.title()).append('|')
                .append(finding.detail()).append('\n'));
    }
}
