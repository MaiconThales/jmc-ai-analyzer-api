package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.BlobObject;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.BlobNotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.BlobProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Acesso ao blob storage (API S3). Toda falha de comunicação vira {@link StorageUnavailableException};
 * objeto inexistente vira {@code false} ou {@link BlobNotFoundException}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlobStorageService {

	static final String CONTENT_TYPE = "application/octet-stream";

	private final S3Client s3;
	private final BlobProperties properties;

	public String bucket() {
		return properties.bucket();
	}

	/** Cria o bucket se ainda não existir. */
	public void ensureBucket() {
		call(() -> {
			try {
				s3.headBucket(b -> b.bucket(bucket()));
			} catch (NoSuchBucketException e) {
				s3.createBucket(b -> b.bucket(bucket()));
				log.info("Bucket '{}' criado", bucket());
			} catch (S3Exception e) {
				if (e.statusCode() != 404) {
					throw e;
				}
				s3.createBucket(b -> b.bucket(bucket()));
				log.info("Bucket '{}' criado", bucket());
			}
			return null;
		});
	}

	/** Verifica se o bucket responde (usado no health check). */
	public void checkBucket() {
		call(() -> s3.headBucket(b -> b.bucket(bucket())));
	}

	public boolean exists(String key) {
		return call(() -> {
			try {
				s3.headObject(b -> b.bucket(bucket()).key(key));
				return true;
			} catch (NoSuchKeyException e) {
				return false;
			} catch (S3Exception e) {
				if (e.statusCode() == 404) {
					return false;
				}
				throw e;
			}
		});
	}

	/** Envia um arquivo local; usa multipart upload acima do limite configurado. */
	public void upload(String key, Path file, Map<String, String> metadata) {
		call(() -> {
			long size = Files.size(file);
			if (size > properties.multipartThreshold().toBytes()) {
				multipartUpload(key, file, metadata);
			} else {
				s3.putObject(b -> b.bucket(bucket()).key(key).contentType(CONTENT_TYPE).metadata(metadata),
						RequestBody.fromFile(file));
			}
			return null;
		});
	}

	/**
	 * Baixa um objeto para o arquivo informado.
	 *
	 * @throws BlobNotFoundException se o objeto não existir
	 */
	public void download(String key, Path target) {
		call(() -> {
			try {
				Files.deleteIfExists(target);
				s3.getObject(b -> b.bucket(bucket()).key(key), target);
			} catch (NoSuchKeyException e) {
				throw new BlobNotFoundException(key);
			}
			return null;
		});
	}

	public void delete(String key) {
		call(() -> s3.deleteObject(b -> b.bucket(bucket()).key(key)));
	}


	/** Lista todos os objetos com o prefixo informado. */
	public List<BlobObject> list(String prefix) {
		return call(() -> {
			List<BlobObject> keys = new ArrayList<>();
			String token = null;
			do {
				String continuation = token;
				ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket())
						.prefix(prefix).continuationToken(continuation).build());
				page.contents().stream().map(o -> new BlobObject(o.key(), o.lastModified())).forEach(keys::add);
				token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
			} while (token != null);
			return keys;
		});
	}

	private void multipartUpload(String key, Path file, Map<String, String> metadata) throws IOException {
		String uploadId = s3.createMultipartUpload(b -> b.bucket(bucket()).key(key).contentType(CONTENT_TYPE)
				.metadata(metadata)).uploadId();
		try (InputStream in = Files.newInputStream(file)) {
			List<CompletedPart> parts = new ArrayList<>();
			byte[] buffer = new byte[(int) properties.multipartPartSize().toBytes()];
			int partNumber = 1;
			int read;
			while ((read = in.readNBytes(buffer, 0, buffer.length)) > 0) {
				int number = partNumber++;
				String etag = s3.uploadPart(b -> b.bucket(bucket()).key(key).uploadId(uploadId).partNumber(number),
						RequestBody.fromBytes(java.util.Arrays.copyOf(buffer, read))).eTag();
				parts.add(CompletedPart.builder().partNumber(number).eTag(etag).build());
			}
			s3.completeMultipartUpload(b -> b.bucket(bucket()).key(key).uploadId(uploadId)
					.multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()));
		} catch (IOException | SdkException e) {
			s3.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket()).key(key)
					.uploadId(uploadId).build());
			throw e;
		}
	}

	private <T> T call(BlobCall<T> action) {
		try {
			return action.run();
		} catch (BlobNotFoundException e) {
			throw e;
		} catch (SdkException | IOException e) {
			throw new StorageUnavailableException("Falha ao acessar o blob storage: " + e.getMessage(), e);
		}
	}

	@FunctionalInterface
	private interface BlobCall<T> {
		T run() throws IOException;
	}

}
