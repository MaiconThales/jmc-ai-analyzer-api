package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

/** Objeto inexistente no blob storage. */
public class BlobNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public BlobNotFoundException(String key) {
		super("Objeto '" + key + "' não encontrado no blob storage.");
	}
}
