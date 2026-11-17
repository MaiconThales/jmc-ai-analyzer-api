package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

/**
 * Configuração pública da API, para o front validar antes de enviar.
 *
 * @param upload     limites do upload
 * @param dashboard  limites do top-N dos dashboards
 * @param retention  padrão de retenção de um software novo
 */
public record ConfigResponse(Upload upload, Dashboard dashboard, Retention retention) {

	/**
	 * @param maxFiles            arquivos por lote
	 * @param maxFileSizeBytes    tamanho máximo de cada arquivo
	 * @param maxRequestSizeBytes tamanho máximo do lote
	 * @param allowedExtension    extensão aceita
	 */
	public record Upload(int maxFiles, long maxFileSizeBytes, long maxRequestSizeBytes, String allowedExtension) {
	}

	/**
	 * @param defaultTopN itens de top-N quando não informado
	 * @param maxTopN     máximo aceito em {@code topN}
	 */
	public record Dashboard(int defaultTopN, int maxTopN) {
	}

	/**
	 * @param defaultDays dias de retenção de um software novo
	 */
	public record Retention(int defaultDays) {
	}
}
