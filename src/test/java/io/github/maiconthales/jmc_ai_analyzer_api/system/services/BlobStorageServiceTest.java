package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing.BlobObject;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.BlobNotFoundException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.BlobProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/** Caminhos de erro do blob storage com um {@link S3Client} simulado. */
class BlobStorageServiceTest {

	@TempDir
	Path tmp;

	private S3Client s3;
	private BlobStorageService service;

	@BeforeEach
	void setUp() {
		s3 = mock(S3Client.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
		service = new BlobStorageService(s3, new BlobProperties("http://x", "bucket", "a", "s", "us-east-1",
				DataSize.ofBytes(10), DataSize.ofBytes(8)));
	}

	@Test
	void ensureBucketCreatesWhenMissing() {
		doThrow(NoSuchBucketException.builder().build()).when(s3).headBucket(any(HeadBucketRequest.class));
		doReturn(null).when(s3).createBucket(any(CreateBucketRequest.class));
		service.ensureBucket();
		verify(s3).createBucket(any(CreateBucketRequest.class));
	}

	@Test
	void ensureBucketCreatesOn404AndFailsOnOtherErrors() {
		doThrow(S3Exception.builder().statusCode(404).build()).when(s3).headBucket(any(HeadBucketRequest.class));
		doReturn(null).when(s3).createBucket(any(CreateBucketRequest.class));
		service.ensureBucket();
		verify(s3).createBucket(any(CreateBucketRequest.class));

		doThrow(S3Exception.builder().statusCode(500).build()).when(s3).headBucket(any(HeadBucketRequest.class));
		assertThatThrownBy(service::ensureBucket).isInstanceOf(StorageUnavailableException.class);
	}

	@Test
	void ensureBucketDoesNothingWhenPresent() {
		doReturn(null).when(s3).headBucket(any(HeadBucketRequest.class));
		service.ensureBucket();
		verify(s3, never()).createBucket(any(CreateBucketRequest.class));
	}

	@Test
	void existsHandlesMissingObjectsAndErrors() {
		doReturn(HeadObjectResponse.builder().build()).when(s3).headObject(any(HeadObjectRequest.class));
		assertThat(service.exists("k")).isTrue();
		doThrow(NoSuchKeyException.builder().build()).when(s3).headObject(any(HeadObjectRequest.class));
		assertThat(service.exists("k")).isFalse();
		doThrow(S3Exception.builder().statusCode(404).build()).when(s3).headObject(any(HeadObjectRequest.class));
		assertThat(service.exists("k")).isFalse();
		doThrow(S3Exception.builder().statusCode(403).build()).when(s3).headObject(any(HeadObjectRequest.class));
		assertThatThrownBy(() -> service.exists("k")).isInstanceOf(StorageUnavailableException.class);
	}

	@Test
	void largeFilesUseMultipartUploadAndAbortOnFailure() throws Exception {
		Path file = Files.write(tmp.resolve("f.bin"), new byte[20]);
		doReturn(CreateMultipartUploadResponse.builder().uploadId("u").build()).when(s3)
				.createMultipartUpload(any(CreateMultipartUploadRequest.class));
		doReturn(UploadPartResponse.builder().eTag("e").build()).when(s3)
				.uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
		doReturn(null).when(s3).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
		service.upload("k", file, Map.of());
		verify(s3).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));

		doThrow(S3Exception.builder().statusCode(500).build()).when(s3)
				.uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
		doReturn(null).when(s3).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
		assertThatThrownBy(() -> service.upload("k", file, Map.of())).isInstanceOf(StorageUnavailableException.class);
		verify(s3).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
	}

	@Test
	void missingLocalFileAndMissingObjectAreReported() {
		Path missing = tmp.resolve("nao-existe");
		assertThatThrownBy(() -> service.upload("k", missing, Map.of())).isInstanceOf(StorageUnavailableException.class);
		doThrow(NoSuchKeyException.builder().build()).when(s3).getObject(any(GetObjectRequest.class), any(Path.class));
		Path target = tmp.resolve("t");
		assertThatThrownBy(() -> service.download("k", target))
				.isInstanceOf(BlobNotFoundException.class);
	}

	@Test
	void listFollowsPagination() {
		doReturn(ListObjectsV2Response.builder().isTruncated(true).nextContinuationToken("t")
				.contents(S3Object.builder().key("a").build()).build(),
				ListObjectsV2Response.builder().isTruncated(false).contents(S3Object.builder().key("b").build()).build())
				.when(s3).listObjectsV2(any(ListObjectsV2Request.class));
		assertThat(service.list("p")).extracting(BlobObject::key).containsExactly("a", "b");
	}
}
