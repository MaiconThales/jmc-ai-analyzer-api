package io.github.maiconthales.jmc_ai_analyzer_api.system.enums;

/** Prioridade de um item de diagnóstico. A ordem da declaração é a ordem de exibição. */
public enum Priority {
	ALTA, MEDIA, BAIXA;

	/**
	 * Converte um score de 0 a 100 em prioridade.
	 *
	 * @param score score do achado, pode ser nulo
	 * @return {@code ALTA} para 75 ou mais, {@code MEDIA} de 25 a 74 e {@code BAIXA} abaixo disso
	 */
	public static Priority fromScore(Double score) {
		if (score == null) {
			return BAIXA;
		}
		if (score >= 75) {
			return ALTA;
		}
		return score >= 25 ? MEDIA : BAIXA;
	}
}
