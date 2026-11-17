package io.github.maiconthales.jmc_ai_analyzer_api.system.properties;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Diretório local de arquivos temporários (upload e processamento).
 *
 * @param tmpDir    diretório dos temporários
 * @param tmpMaxAge idade a partir da qual um temporário esquecido é apagado
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
		@NotNull Path tmpDir,
		@NotNull @DefaultValue("1h") Duration tmpMaxAge) {
}
