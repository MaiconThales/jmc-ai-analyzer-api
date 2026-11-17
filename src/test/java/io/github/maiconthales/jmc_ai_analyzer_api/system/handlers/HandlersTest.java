package io.github.maiconthales.jmc_ai_analyzer_api.system.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.BlobStorageHealthIndicator;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.ApiException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.NotReadyException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.StorageUnavailableException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.BlobStorageService;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class HandlersTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();
	private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

	@Test
	void apiExceptionsCarryCodeAndProperties() {
		ResponseEntity<ProblemDetail> response = handler.handleApi(new NotReadyException("x").with("status", "PENDING"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().getProperties()).containsEntry("code", "NOT_READY").containsEntry("status", "PENDING");
		assertThat(handler.handleApi(new StorageUnavailableException("fora", null)).getStatusCode())
				.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void frameworkExceptionsAreMapped() throws Exception {
		assertThat(handler.handleConstraintViolation(new ConstraintViolationException("x", Set.of())).getBody()
				.getProperties()).containsEntry("code", "INVALID_PARAMETER");
		ResponseEntity<ProblemDetail> unexpected = handler.handleUnexpected(new IllegalStateException("segredo"));
		assertThat(unexpected.getBody().getDetail()).doesNotContain("segredo");
		ResponseEntity<Object> tooLarge = handler.handleException(new MaxUploadSizeExceededException(10), request);
		assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(((ProblemDetail) tooLarge.getBody()).getProperties()).containsEntry("code", "FILE_TOO_LARGE");
		ResponseEntity<Object> missing = handler.handleException(new MissingServletRequestPartException("other"), request);
		assertThat(((ProblemDetail) missing.getBody()).getProperties()).containsEntry("code", "INVALID_PARAMETER");
		ResponseEntity<Object> method = handler.handleException(new HttpRequestMethodNotSupportedException("PUT"), request);
		assertThat(((ProblemDetail) method.getBody()).getProperties()).containsEntry("code", "INVALID_PARAMETER");
	}

	@Test
	void statusCodesMapToErrorCodes() {
		assertThat(ApiExceptionHandler.codeFor(HttpStatus.NOT_FOUND)).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(ApiExceptionHandler.codeFor(HttpStatus.CONTENT_TOO_LARGE)).isEqualTo(ErrorCode.FILE_TOO_LARGE);
		assertThat(ApiExceptionHandler.codeFor(HttpStatus.UNSUPPORTED_MEDIA_TYPE)).isEqualTo(ErrorCode.INVALID_PARAMETER);
		assertThat(ApiExceptionHandler.codeFor(HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo(ErrorCode.INTERNAL_ERROR);
		assertThat(new ApiException(ErrorCode.INTERNAL_ERROR, "x", new IllegalStateException()).getCause()).isNotNull();
	}

	@Test
	void healthIndicatorReportsDown() {
		BlobStorageService blob = mock(BlobStorageService.class);
		when(blob.bucket()).thenReturn("b");
		doThrow(new StorageUnavailableException("fora", null)).when(blob).checkBucket();
		assertThat(new BlobStorageHealthIndicator(blob).health().getStatus()).isEqualTo(Status.DOWN);
	}
}
