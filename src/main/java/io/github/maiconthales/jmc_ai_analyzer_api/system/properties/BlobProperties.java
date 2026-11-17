package io.github.maiconthales.jmc_ai_analyzer_api.system.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Conexão com o blob storage compatível com S3 (SeaweedFS).
 *
 * @param endpoint           URL do gateway S3
 * @param bucket             bucket dos arquivos JFR
 * @param accessKey          chave de acesso
 * @param secretKey          segredo de acesso
 * @param region             região informada ao SDK (o SeaweedFS ignora)
 * @param multipartThreshold tamanho a partir do qual o envio usa multipart upload
 * @param multipartPartSize  tamanho de cada parte do multipart upload
 */
@Validated
@ConfigurationProperties(prefix = "app.blob")
public record BlobProperties(
		@NotBlank String endpoint,
		@NotBlank @DefaultValue("jfr-recordings") String bucket,
		@NotBlank String accessKey,
		@NotBlank String secretKey,
		@NotBlank @DefaultValue("us-east-1") String region,
		@NotNull @DefaultValue("64MB") DataSize multipartThreshold,
		@NotNull @DefaultValue("16MB") DataSize multipartPartSize) {
}
