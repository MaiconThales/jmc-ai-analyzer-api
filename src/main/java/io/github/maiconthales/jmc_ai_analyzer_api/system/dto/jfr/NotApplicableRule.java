package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import java.util.List;

/**
 * Regra do JMC que não pôde ser avaliada por falta de eventos.
 *
 * @param ruleId            identificador da regra
 * @param requiredEvents    tipos de evento que a regra exige
 * @param suggestedSettings configurações sugeridas pelo JMC ({@code tipo#nome=valor})
 */
public record NotApplicableRule(String ruleId, List<String> requiredEvents, List<String> suggestedSettings) {
}
