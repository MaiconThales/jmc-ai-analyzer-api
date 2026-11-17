package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;

/** Upload rejeitado por uma das validações. */
public class InvalidUploadException extends ApiException {

	private static final long serialVersionUID = 1L;

	public InvalidUploadException(ErrorCode code, String detail) {
		super(code, detail);
	}
}
