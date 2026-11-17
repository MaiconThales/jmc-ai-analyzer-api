package io.github.maiconthales.jmc_ai_analyzer_api.system.controllers;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.DashboardDtos.RecordingDashboard;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.dashboard.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard de uma gravação. */
@Tag(name = "Gravações", description = "Dashboard de uma gravação JFR")
@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

	private final DashboardService dashboardService;

	@Operation(summary = "Dashboard da gravação",
			description = "Diagnóstico priorizado, cobertura, métricas por domínio e achados do JMC.")
	@ApiResponse(responseCode = "404", description = "Gravação inexistente (problem+json)")
	@ApiResponse(responseCode = "409", description = "Gravação não concluída (problem+json)")
	@GetMapping("/{recordingId}/dashboard")
	public RecordingDashboard dashboard(@PathVariable UUID recordingId,
			@Parameter(description = "Itens de top-N (1 a 50)") @RequestParam(required = false) Integer topN) {
		return dashboardService.recording(recordingId, topN);
	}
}
