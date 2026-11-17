package io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions;

/** Processamento interrompido (timeout). */
public class AnalysisInterruptedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AnalysisInterruptedException() {
		super("Processamento interrompido");
	}
}
