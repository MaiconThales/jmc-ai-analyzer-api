package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

/** Arquivo JFR ilegível (truncado, corrompido ou de formato desconhecido). */
public class JfrParseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public JfrParseException(String message, Throwable cause) {
		super(message, cause);
	}
}
