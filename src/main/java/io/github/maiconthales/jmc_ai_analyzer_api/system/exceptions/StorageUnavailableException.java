package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;

/** Blob storage fora do ar ou respondendo com erro. */
public class StorageUnavailableException extends ApiException {

	private static final long serialVersionUID = 1L;

	public StorageUnavailableException(String detail, Throwable cause) {
		super(ErrorCode.STORAGE_UNAVAILABLE, detail, cause);
	}
}
