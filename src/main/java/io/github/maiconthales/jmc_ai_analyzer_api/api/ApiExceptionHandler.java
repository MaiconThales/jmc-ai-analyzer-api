package io.github.maiconthales.jmc_ai_analyzer_api.api;

import io.github.maiconthales.jmc_ai_analyzer_api.application.AnalysisQueryService;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.InvalidJfrFileIdException;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.JfrFileNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Central HTTP boundary: never reflects exception details to clients. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingPathVariableException.class})
    ResponseEntity<ApiError> validation(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request is invalid.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException exception,
                                           HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request is invalid.", request);
    }

    @ExceptionHandler(InvalidUuidException.class)
    ResponseEntity<ApiError> uuid(InvalidUuidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_UUID", "The identifier is invalid.", request);
    }

    @ExceptionHandler(InvalidJfrFileIdException.class)
    ResponseEntity<ApiError> invalidFile(InvalidJfrFileIdException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_FILE", "The file identifier is invalid.", request);
    }

    @ExceptionHandler({JfrFileNotFoundException.class, AnalysisQueryService.AnalysisNotFoundException.class,
            NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception exception, HttpServletRequest request) {
        String code = exception instanceof JfrFileNotFoundException ? "FILE_NOT_FOUND"
                : exception instanceof NoResourceFoundException ? "NOT_FOUND" : "ANALYSIS_NOT_FOUND";
        String message = exception instanceof JfrFileNotFoundException ? "The requested file was not found."
                : exception instanceof NoResourceFoundException ? "The requested resource was not found."
                : "The requested analysis was not found.";
        return error(HttpStatus.NOT_FOUND, code, message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request cannot be processed.", request);
    }

    @ExceptionHandler({ApiConflictException.class, DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with the current state.", request);
    }

    @ExceptionHandler({ApiOverloadException.class, RejectedExecutionException.class})
    ResponseEntity<ApiError> overload(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "OVERLOAD", "The service is temporarily busy.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR",
                "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null && !correlationId.matches("[A-Za-z0-9._:-]{1,128}")) correlationId = null;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(Instant.now(), status.value(), code, message, correlationId));
    }
}
