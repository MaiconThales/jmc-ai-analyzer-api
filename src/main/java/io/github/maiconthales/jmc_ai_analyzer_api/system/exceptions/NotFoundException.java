package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;

/** Recurso inexistente. */
public class NotFoundException extends ApiException {

	private static final long serialVersionUID = 1L;

	public NotFoundException(String resource, Object id) {
		super(ErrorCode.NOT_FOUND, resource + " '" + id + "' não encontrado.");
	}
}
