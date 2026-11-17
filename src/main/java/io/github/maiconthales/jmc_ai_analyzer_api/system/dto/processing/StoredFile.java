package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.processing;

/**
 * Arquivo já enviado ao blob storage, pronto para virar uma gravação.
 *
 * @param originalName nome original sanitizado
 * @param key          chave no blob
 * @param sha256       hash do conteúdo
 * @param sizeBytes    tamanho em bytes
 */
public record StoredFile(String originalName, String key, String sha256, long sizeBytes) {
}
