package io.github.maiconthales.jmc_ai_analyzer_api.system.controllers;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.BatchResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.BatchDashboard;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.HistoryItemResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.UploadRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.BatchQueryService;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.HistoryService;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.UploadService;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload, status, dashboard consolidado e histórico de lotes. */
@Tag(name = "Análises", description = "Upload de gravações JFR e consulta dos lotes")
@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
public class AnalysisController {

	private final UploadService uploadService;
	private final BatchQueryService batchQueryService;
	private final DashboardService dashboardService;
	private final HistoryService historyService;

	@Operation(summary = "Envia um lote de arquivos .jfr",
			description = "Valida e guarda os arquivos e inicia o processamento assíncrono de cada gravação.")
	@ApiResponse(responseCode = "202", description = "Lote aceito")
	@ApiResponse(responseCode = "400", description = "Parâmetro ou arquivo inválido (problem+json)")
	@ApiResponse(responseCode = "413", description = "Arquivo ou lote grande demais (problem+json)")
	@ApiResponse(responseCode = "503", description = "Blob storage indisponível (problem+json)")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<BatchResponse> upload(
			@Parameter(description = "Identificador do software (slug), ex.: billing-service")
			@RequestParam(value = "software", required = false) String software,
			@Parameter(description = "Versão do software") @RequestParam(required = false) String softwareVersion,
			@Parameter(description = "PROD, HML, DEV ou LOCAL") @RequestParam(required = false) String environment,
			@Parameter(description = "Contexto do problema (até 500 caracteres)")
			@RequestParam(required = false) String description,
			@Parameter(description = "Arquivos .jfr") @RequestPart("files") List<MultipartFile> files) {
		BatchResponse response = uploadService.upload(
				new UploadRequest(software, softwareVersion, environment, description), files);
		return ResponseEntity.accepted().location(URI.create("/api/v1/analyses/" + response.batchId())).body(response);
	}

	@Operation(summary = "Histórico paginado de lotes", description = "Filtro opcional por software.")
	@GetMapping
	public Page<HistoryItemResponse> history(@RequestParam(required = false) String software,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return historyService.list(software, pageable);
	}

	@Operation(summary = "Status do lote e das gravações")
	@ApiResponse(responseCode = "404", description = "Lote inexistente (problem+json)")
	@GetMapping("/{batchId}")
	public BatchResponse status(@PathVariable UUID batchId) {
		return batchQueryService.get(batchId);
	}

	@Operation(summary = "Dashboard consolidado do lote", description = "Agrega somente as gravações concluídas.")
	@ApiResponse(responseCode = "409", description = "Nenhuma gravação concluída (problem+json)")
	@GetMapping("/{batchId}/dashboard")
	public BatchDashboard dashboard(@PathVariable UUID batchId,
			@Parameter(description = "Itens de top-N (1 a 50)") @RequestParam(required = false) Integer topN) {
		return dashboardService.batch(batchId, topN);
	}
}
