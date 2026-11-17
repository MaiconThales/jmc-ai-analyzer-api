package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import java.util.List;

/** Resultado: itens por domínio e comando sugerido. */
public record CoverageResult(List<CoverageItem> items, String suggestedCommand) {
}
