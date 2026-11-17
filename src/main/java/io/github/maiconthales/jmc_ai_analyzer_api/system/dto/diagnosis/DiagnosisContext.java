package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import java.util.List;

/**
 * Entrada das regras de diagnóstico.
 *
 * @param extraction  dados extraídos da gravação
 * @param environment ambiente informado no upload (pode ser nulo)
 * @param appPackages pacotes da aplicação cadastrados no software
 * @param coverage    cobertura já calculada
 * @param properties  limites configurados
 */
public record DiagnosisContext(RecordingExtraction extraction, Environment environment, List<String> appPackages,
		List<CoverageItem> coverage, DiagnosisProperties properties) {
}
