package io.github.maiconthales.jmc_ai_analyzer_api.system.components;

import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.BlobStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Saúde do blob storage: o bucket precisa responder ao {@code HeadBucket}. */
@Component("blobStorage")
@RequiredArgsConstructor
public class BlobStorageHealthIndicator implements HealthIndicator {

	private final BlobStorageService blobStorage;

	@Override
	public Health health() {
		try {
			blobStorage.checkBucket();
			return Health.up().withDetail("bucket", blobStorage.bucket()).build();
		} catch (StorageUnavailableException e) {
			return Health.down().withDetail("bucket", blobStorage.bucket()).withDetail("error", e.getMessage()).build();
		}
	}
}
