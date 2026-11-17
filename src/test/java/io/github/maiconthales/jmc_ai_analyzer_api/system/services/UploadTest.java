package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.maiconthales.jmc_ai_analyzer_api.config.TestProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.support.JfrFixtures;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.UploadValidator;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.UploadRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.InvalidUploadException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.StorageProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.UploadProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

class UploadTest {

	@TempDir
	Path tmp;

	private final UploadValidator validator = new UploadValidator(TestProperties.upload());
	private BlobStorageService blob;
	private BatchCreationService creator;
	private UploadService service;

	@BeforeEach
	void setUp() {
		blob = mock(BlobStorageService.class);
		creator = mock(BatchCreationService.class);
		TempFileService temp = new TempFileService(new StorageProperties(tmp.resolve("t"), Duration.ofHours(1)),
				Clock.systemUTC());
		service = new UploadService(validator, temp, blob, creator);
	}

	@Test
	void validatorAcceptsValidRequestAndParsesEnvironment() {
		assertThat(validator.validate(new UploadRequest("billing-service", "1.0", " hml ", "x"), List.of(jfr("a.JFR"))))
				.isEqualTo(Environment.HML);
		assertThat(validator.validate(new UploadRequest("billing-service", null, "", null), List.of(jfr("a.jfr"))))
				.isNull();
	}

	@Test
	void validatorRejectsInvalidFields() {
		assertCode(new UploadRequest(null, null, null, null), List.of(jfr("a.jfr")), ErrorCode.INVALID_PARAMETER);
		assertCode(new UploadRequest("Billing", null, null, null), List.of(jfr("a.jfr")), ErrorCode.INVALID_PARAMETER);
		assertCode(new UploadRequest("app", "v".repeat(65), null, null), List.of(jfr("a.jfr")), ErrorCode.INVALID_PARAMETER);
		assertCode(new UploadRequest("app", null, null, "d".repeat(501)), List.of(jfr("a.jfr")), ErrorCode.INVALID_PARAMETER);
		assertCode(new UploadRequest("app", null, "QA", null), List.of(jfr("a.jfr")), ErrorCode.INVALID_PARAMETER);
	}

	@Test
	void validatorRejectsInvalidFiles() {
		UploadRequest ok = new UploadRequest("app", null, null, null);
		assertCode(ok, null, ErrorCode.EMPTY_UPLOAD);
		assertCode(ok, List.of(), ErrorCode.EMPTY_UPLOAD);
		assertCode(ok, List.of(jfr("a.jfr"), new MockMultipartFile("files", "b.jfr", null, new byte[0])), ErrorCode.EMPTY_UPLOAD);
		assertCode(ok, List.of(new MockMultipartFile("files", null, null, new byte[] {1})), ErrorCode.INVALID_FILE);
		UploadValidator small = new UploadValidator(new UploadProperties(1, DataSize.ofBytes(10), DataSize.ofBytes(15)));
		assertThatThrownBy(() -> small.validate(ok, List.of(jfr("a.jfr"), jfr("b.jfr"))))
				.extracting(e -> ((InvalidUploadException) e).getCode()).isEqualTo(ErrorCode.TOO_MANY_FILES);
		MockMultipartFile big = new MockMultipartFile("files", "a.jfr", null, new byte[11]);
		assertThatThrownBy(() -> small.validate(ok, List.of(big)))
				.extracting(e -> ((InvalidUploadException) e).getCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
		UploadValidator total = new UploadValidator(new UploadProperties(5, DataSize.ofBytes(10), DataSize.ofBytes(15)));
		MockMultipartFile eight = new MockMultipartFile("files", "a.jfr", null, new byte[8]);
		assertThatThrownBy(() -> total.validate(ok, List.of(eight, eight)))
				.extracting(e -> ((InvalidUploadException) e).getCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
	}

	@Test
	void uploadStoresNewObjectsAndSkipsExistingOnes() {
		when(blob.exists(anyString())).thenReturn(false, true);
		MockMultipartFile a = jfr("dir/a.jfr");
		MockMultipartFile b = jfr("b.jfr");
		service.upload(new UploadRequest("app", null, null, null), List.of(a, b));
		verify(blob).upload(anyString(), any(Path.class), anyMap());
		verify(creator).create(any(), eq(null), any());
	}

	@Test
	void failureDeletesOnlyObjectsCreatedInTheRequest() {
		when(blob.exists(anyString())).thenReturn(false);
		doThrow(new StorageUnavailableException("fora", null)).when(creator).create(any(), any(), any());
		List<MultipartFile> files = List.of(jfr("a.jfr"));
		UploadRequest request = new UploadRequest("app", null, null, null);
		assertThatThrownBy(() -> service.upload(request, files)).isInstanceOf(StorageUnavailableException.class);
		verify(blob).delete(anyString());
	}

	@Test
	void invalidMagicIsRejectedBeforeReachingTheBlob() {
		MultipartFile bad = new MockMultipartFile("files", "bad.jfr", null, "nao".getBytes());
		UploadRequest request = new UploadRequest("app", null, null, null);
		assertThatThrownBy(() -> service.upload(request, List.of(bad)))
				.extracting(e -> ((InvalidUploadException) e).getCode()).isEqualTo(ErrorCode.INVALID_FILE);
		verify(blob, never()).upload(anyString(), any(), anyMap());
	}

	@Test
	void unreadableUploadAndCleanupErrorsAreHandled() throws IOException {
		MultipartFile broken = mock(MultipartFile.class);
		when(broken.getOriginalFilename()).thenReturn("a.jfr");
		when(broken.getSize()).thenReturn(10L);
		when(broken.getInputStream()).thenThrow(new IOException("falhou"));
		when(blob.exists(anyString())).thenReturn(false);
		UploadRequest request = new UploadRequest("app", null, null, null);
		assertThatThrownBy(() -> service.upload(request, List.of(broken))).hasMessageContaining("Falha ao ler");

		doThrow(new StorageUnavailableException("fora", null)).when(blob).delete(anyString());
		doThrow(new IllegalStateException("boom")).when(creator).create(any(), any(), any());
		List<MultipartFile> files = List.of(jfr("a.jfr"));
		assertThatThrownBy(() -> service.upload(request, files)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void sanitizeRemovesPathsAndControlCharacters() {
		assertThat(UploadService.sanitize(null)).isEqualTo("recording.jfr");
		assertThat(UploadService.sanitize("C:\\\\tmp\\\\x.jfr")).isEqualTo("x.jfr");
		assertThat(UploadService.sanitize("/etc/../a\u0001b.jfr")).isEqualTo("ab.jfr");
		assertThat(UploadService.sanitize("dir/")).isEqualTo("recording.jfr");
		assertThat(UploadService.sanitize("n".repeat(300) + ".jfr")).hasSize(255).endsWith(".jfr");
	}

	private void assertCode(UploadRequest request, List<MultipartFile> files, ErrorCode code) {
		assertThatThrownBy(() -> validator.validate(request, files)).isInstanceOf(InvalidUploadException.class)
				.extracting(e -> ((InvalidUploadException) e).getCode()).isEqualTo(code);
	}

	private static MockMultipartFile jfr(String name) {
		return new MockMultipartFile("files", name, "application/octet-stream", JfrFixtures.bytes(JfrFixtures.example(0)));
	}
}
