package io.github.maiconthales.jmc_ai_analyzer_api.system.controllers;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.ConfigResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.services.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuração pública consumida pelo front. */
@Tag(name = "Configuração", description = "Limites usados pelo front para validar antes de enviar")
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

	private final ConfigService configService;

	@Operation(summary = "Limites de upload, de top-N e retenção padrão")
	@GetMapping
	public ConfigResponse config() {
		return configService.current();
	}
}
