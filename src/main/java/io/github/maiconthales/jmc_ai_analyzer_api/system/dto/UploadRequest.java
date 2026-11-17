package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

/**
 * Campos de texto do upload, ainda sem validação.
 *
 * @param software        identificador do software (obrigatório)
 * @param softwareVersion versão (opcional)
 * @param environment     ambiente (opcional)
 * @param description     descrição do contexto (opcional)
 */
public record UploadRequest(String software, String softwareVersion, String environment, String description) {
}
