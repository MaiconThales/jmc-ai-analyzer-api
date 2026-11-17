package io.github.maiconthales.jmc_ai_analyzer_api.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Alteração parcial de um software. Campos nulos não são alterados.
 *
 * @param retentionDays dias de retenção (1 a 3650)
 * @param appPackages   prefixos de pacote da aplicação (até 20)
 */
public record SoftwareUpdateRequest(
		@Min(1) @Max(3650) Integer retentionDays,
		@Size(max = 20) List<@Pattern(regexp = SoftwareUpdateRequest.PACKAGE_REGEX,
				message = "prefixo de pacote Java inválido") String> appPackages) {

	public static final String PACKAGE_REGEX = "^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*\\.?$";
}
