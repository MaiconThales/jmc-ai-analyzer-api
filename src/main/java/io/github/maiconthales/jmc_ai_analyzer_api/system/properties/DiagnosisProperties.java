package io.github.maiconthales.jmc_ai_analyzer_api.system.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Limites das regras de diagnóstico, pesos da nota de saúde e mínimos da cobertura.
 */
@Validated
@ConfigurationProperties(prefix = "app.diagnosis")
public record DiagnosisProperties(
		@Valid @NotNull @DefaultValue PriorityScore priorityScore,
		@Valid @NotNull @DefaultValue HealthWeights healthWeights,
		@NotNull @DefaultValue({"java.", "javax.", "jdk.", "sun.", "com.sun.", "org.springframework.",
				"org.apache.", "io.netty."}) List<String> frameworkPackages,
		@DefaultValue("85") double heapPressurePct,
		@DefaultValue("200") double gcPauseMaxMs,
		@DefaultValue("5") double gcPausePct,
		@NotNull @DefaultValue("1GB") DataSize allocationRate,
		@DefaultValue("90") double cpuSaturationPct,
		@DefaultValue("10") double cpuSaturationTimePct,
		@DefaultValue("20") double hotMethodPct,
		@DefaultValue("1") double lockTimePct,
		@DefaultValue("100") double exceptionRatePerSecond,
		@DefaultValue("1000") double slowSocketReadMs,
		@DefaultValue("2") double threadGrowthRatio,
		@DefaultValue("0.5") double xmsXmxMinRatio,
		@Valid @NotNull @DefaultValue Coverage coverage) {

	/** Score atribuído a um item de diagnóstico conforme a prioridade padrão da regra. */
	public record PriorityScore(
			@DefaultValue("80") int alta,
			@DefaultValue("50") int media,
			@DefaultValue("15") int baixa) {
	}

	/** Penalidade, na nota de saúde, de cada item conforme a prioridade. */
	public record HealthWeights(
			@DefaultValue("20") int alta,
			@DefaultValue("8") int media,
			@DefaultValue("2") int baixa) {
	}

	/** Mínimos para considerar um domínio bem coberto pela gravação. */
	public record Coverage(
			@DefaultValue("1000") long minExecutionSamples,
			@NotNull @DefaultValue("5m") Duration minDuration) {
	}
}
