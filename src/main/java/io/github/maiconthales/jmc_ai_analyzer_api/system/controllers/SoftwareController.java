package io.github.maiconthales.jmc_ai_analyzer_api.system.controllers;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.SoftwareResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.SoftwareUpdateRequest;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.SoftwareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Softwares cadastrados: retenção e pacotes da aplicação. */
@Tag(name = "Software", description = "Retenção e pacotes da aplicação por software")
@RestController
@RequestMapping("/api/v1/software")
@RequiredArgsConstructor
public class SoftwareController {

	private final SoftwareService softwareService;

	@Operation(summary = "Lista os softwares")
	@GetMapping
	public List<SoftwareResponse> list() {
		return softwareService.list();
	}

	@Operation(summary = "Altera retenção e pacotes da aplicação",
			description = "Campos ausentes não são alterados. 'appPackages' passa a valer também para análises antigas.")
	@ApiResponse(responseCode = "400", description = "Valor inválido (problem+json)")
	@ApiResponse(responseCode = "404", description = "Software inexistente (problem+json)")
	@PatchMapping("/{name}")
	public SoftwareResponse update(@PathVariable String name, @Valid @RequestBody SoftwareUpdateRequest request) {
		return softwareService.update(name, request);
	}
}
