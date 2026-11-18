package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;

import java.util.List;

/**
 * Achados persistíveis e regras não aplicáveis.
 */
public record JmcRulesResult(List<RuleFinding> findings, List<NotApplicableRule> notApplicable) {

    public static JmcRulesResult empty() {
        return new JmcRulesResult(List.of(), List.of());
    }
}
