package io.github.maiconthales.jmc_ai_analyzer_api.system.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Limites do upload de arquivos JFR.
 *
 * @param maxFiles       quantidade máxima de arquivos por lote
 * @param maxFileSize    tamanho máximo de cada arquivo
 * @param maxRequestSize tamanho máximo da requisição inteira
 */
@Validated
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
		@Min(1) @DefaultValue("20") int maxFiles,
		@NotNull @DefaultValue("128MB") DataSize maxFileSize,
		@NotNull @DefaultValue("1GB") DataSize maxRequestSize) {
}
