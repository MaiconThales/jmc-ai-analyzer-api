package io.github.maiconthales.jmc_ai_analyzer_api.system.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retenção do histórico por software.
 *
 * @param defaultDays dias de retenção de um software novo
 * @param cron        agenda do job de retenção
 */
@Validated
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(
		@Min(1) @Max(3650) @DefaultValue("90") int defaultDays,
		@NotBlank @DefaultValue("0 0 3 * * *") String cron) {
}
