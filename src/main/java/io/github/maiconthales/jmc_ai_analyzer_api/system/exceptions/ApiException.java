package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Erro de negócio com código padronizado, convertido em {@code application/problem+json}. */
public class ApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final ErrorCode code;
	private final transient Map<String, Object> properties = new LinkedHashMap<>();

	public ApiException(ErrorCode code, String detail) {
		super(detail);
		this.code = code;
	}

	public ApiException(ErrorCode code, String detail, Throwable cause) {
		super(detail, cause);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}

	/** Propriedades extras incluídas no corpo do erro. */
	public Map<String, Object> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	/**
	 * Acrescenta uma propriedade ao corpo do erro.
	 *
	 * @return a própria exceção, para encadear chamadas
	 */
	public ApiException with(String name, Object value) {
		properties.put(name, value);
		return this;
	}
}
