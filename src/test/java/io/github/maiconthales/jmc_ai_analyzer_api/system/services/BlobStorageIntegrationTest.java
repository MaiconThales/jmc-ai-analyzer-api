package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.maiconthales.jmc_ai_analyzer_api.support.Containers;
import io.github.maiconthales.jmc_ai_analyzer_api.support.JfrFixtures;
import io.github.maiconthales.jmc_ai_analyzer_api.system.config.BlobStorageConfig;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.BlobObject;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.BlobProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.S3Client;

/** Compatibilidade real com o SeaweedFS: bucket, envio simples e multipart, leitura, listagem e exclusão. */
class BlobStorageIntegrationTest {

	@TempDir
	Path tmp;

	@Test
	void roundTripWithSimpleAndMultipartUploads() throws Exception {
		BlobProperties properties = new BlobProperties(Containers.seaweedEndpoint(), "it-bucket", Containers.ACCESS_KEY,
				Containers.SECRET_KEY, "us-east-1", DataSize.ofKilobytes(512), DataSize.ofMegabytes(5));
		try (S3Client s3 = new BlobStorageConfig().s3Client(properties)) {
			BlobStorageService service = new BlobStorageService(s3, properties);
			service.ensureBucket();
			service.ensureBucket();
			service.checkBucket();
			Path example = JfrFixtures.example(0);
			Path small = Files.write(tmp.resolve("small.bin"), new byte[] {1, 2, 3});

			service.upload("it/large.jfr", example, Map.of("sha256", "x"));
			service.upload("it/small.bin", small, Map.of());

			assertThat(service.exists("it/large.jfr")).isTrue();
			assertThat(service.exists("it/none")).isFalse();
			Path downloaded = tmp.resolve("down.jfr");
			service.download("it/large.jfr", downloaded);
			assertThat(Files.readAllBytes(downloaded)).isEqualTo(Files.readAllBytes(example));
			assertThat(service.list("it/")).extracting(BlobObject::key)
					.containsExactlyInAnyOrder("it/large.jfr", "it/small.bin");
			service.delete("it/small.bin");
			assertThat(service.exists("it/small.bin")).isFalse();
		}
	}
}
