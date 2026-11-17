package io.github.maiconthales.jmc_ai_analyzer_api.system.enums;

import org.springframework.http.HttpStatus;

/** Códigos de erro expostos pela API e gravados nas gravações que falharam. */
public enum ErrorCode {
	INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Parâmetro inválido"),
	EMPTY_UPLOAD(HttpStatus.BAD_REQUEST, "Nenhum arquivo enviado"),
	TOO_MANY_FILES(HttpStatus.BAD_REQUEST, "Arquivos demais"),
	INVALID_FILE(HttpStatus.BAD_REQUEST, "Arquivo inválido"),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Arquivo grande demais"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),
	NOT_READY(HttpStatus.CONFLICT, "Análise ainda não concluída"),
	STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Armazenamento indisponível"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno"),
	PARSE_ERROR(HttpStatus.UNPROCESSABLE_CONTENT, "Gravação JFR ilegível"),
	TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Tempo de processamento excedido"),
	BLOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Arquivo não encontrado no armazenamento");

	private final HttpStatus status;
	private final String title;

	ErrorCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
	}

	public HttpStatus status() {
		return status;
	}

	public String title() {
		return title;
	}
}
