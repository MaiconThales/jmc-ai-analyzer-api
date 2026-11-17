package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.ErrorCode;

/** Dashboard pedido antes de a análise estar concluída. */
public class NotReadyException extends ApiException {

	private static final long serialVersionUID = 1L;

	public NotReadyException(String detail) {
		super(ErrorCode.NOT_READY, detail);
	}
}
