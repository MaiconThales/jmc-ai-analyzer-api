package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis;

import java.util.List;

/** Eventos de cada domínio, recomendação e configuração extra para o comando sugerido. */
public record CoverageDomainSpec(List<String> eventTypes, String recommendation, String extraSetting) {
}
