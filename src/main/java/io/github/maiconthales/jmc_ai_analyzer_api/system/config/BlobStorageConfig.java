package io.github.maiconthales.jmc_ai_analyzer_api.system.config;

import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.BlobProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Cliente S3 para o SeaweedFS. Os checksums automáticos do SDK (2.30+) ficam em {@code WHEN_REQUIRED},
 * porque servidores compatíveis com S3 costumam rejeitá-los.
 */
@Configuration(proxyBeanMethods = false)
public class BlobStorageConfig {

	@Bean(destroyMethod = "close")
	public S3Client s3Client(BlobProperties properties) {
		return S3Client.builder()
				.endpointOverride(URI.create(properties.endpoint()))
				.region(Region.of(properties.region()))
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
				.forcePathStyle(true)
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.build();
	}
}
