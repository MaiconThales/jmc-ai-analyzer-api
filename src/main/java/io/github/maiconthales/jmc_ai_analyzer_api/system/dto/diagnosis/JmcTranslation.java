package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;

/** Tradução de uma regra do JMC. */
public record JmcTranslation(DiagnosisCategory category, String title, String action, String domainRef) {
}
