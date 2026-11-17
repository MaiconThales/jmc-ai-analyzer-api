package io.github.maiconthales.jmc_ai_analyzer_api.system.handlers;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.ApiException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte todas as exceções em {@code application/problem+json} (RFC 9457) com a propriedade {@code code}.
 * Nunca expõe stack trace.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	static final String CODE = "code";

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
		ErrorCode code = ex.getCode();
		ProblemDetail body = problem(code, ex.getMessage());
		ex.getProperties().forEach(body::setProperty);
		if (code == ErrorCode.STORAGE_UNAVAILABLE) {
			log.warn("Blob storage indisponível: {}", ex.getMessage(), ex.getCause());
		}
		return ResponseEntity.status(code.status()).body(body);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
		return ResponseEntity.badRequest().body(problem(ErrorCode.INVALID_PARAMETER, ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.badRequest().body(problem(ErrorCode.INVALID_PARAMETER,
				"Valor inválido para '" + ex.getName() + "'."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Erro inesperado", ex);
		return ResponseEntity.internalServerError()
				.body(problem(ErrorCode.INTERNAL_ERROR, "Ocorreu um erro inesperado."));
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
				.body(problem(ErrorCode.FILE_TOO_LARGE, "O upload excede o tamanho máximo permitido."));
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ErrorCode code = "files".equals(ex.getRequestPartName()) ? ErrorCode.EMPTY_UPLOAD : ErrorCode.INVALID_PARAMETER;
		return ResponseEntity.badRequest().body(problem(code, "Parte obrigatória ausente: " + ex.getRequestPartName()));
	}

	/** Acrescenta {@code code} às respostas das exceções padrão do Spring MVC. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail detail && detail.getProperties() == null) {
			detail.setProperty(CODE, codeFor(statusCode).name());
		}
		return response;
	}

	static ErrorCode codeFor(HttpStatusCode status) {
		if (status.value() == HttpStatus.NOT_FOUND.value()) {
			return ErrorCode.NOT_FOUND;
		}
		if (status.value() == HttpStatus.CONTENT_TOO_LARGE.value()) {
			return ErrorCode.FILE_TOO_LARGE;
		}
		return status.is4xxClientError() ? ErrorCode.INVALID_PARAMETER : ErrorCode.INTERNAL_ERROR;
	}

	static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
		body.setTitle(code.title());
		body.setProperty(CODE, code.name());
		return body;
	}
}
