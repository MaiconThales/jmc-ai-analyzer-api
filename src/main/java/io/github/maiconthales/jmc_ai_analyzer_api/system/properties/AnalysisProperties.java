package io.github.maiconthales.jmc_ai_analyzer_api.system.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parâmetros do processamento das gravações.
 *
 * @param maxConcurrent   gravações processadas em paralelo
 * @param timeout         tempo máximo de processamento de uma gravação
 * @param maxSeriesPoints pontos máximos por série temporal
 * @param persistedTopN   itens de top-N gravados por domínio
 * @param defaultTopN     itens de top-N devolvidos quando a consulta não informa
 * @param maxTopN         máximo de itens de top-N aceito na consulta
 * @param ruleThreads     threads usadas pelo motor de regras do JMC
 * @param parserVersion   versão do extrator gravada em cada análise
 */
@Validated
@ConfigurationProperties(prefix = "app.analysis")
public record AnalysisProperties(
		@Min(1) @DefaultValue("2") int maxConcurrent,
		@NotNull @DefaultValue("10m") Duration timeout,
		@Min(10) @DefaultValue("300") int maxSeriesPoints,
		@Min(1) @Max(500) @DefaultValue("50") int persistedTopN,
		@Min(1) @DefaultValue("10") int defaultTopN,
		@Min(1) @DefaultValue("50") int maxTopN,
		@Min(1) @DefaultValue("2") int ruleThreads,
		@NotBlank @DefaultValue("dev") String parserVersion) {
}
