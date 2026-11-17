package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import java.util.Optional;

/** Regra própria do catálogo de diagnóstico. */
public interface DiagnosisRule {

	/** Identificador usado em {@code source} como {@code APP:<id>}. */
	String id();

	Optional<DiagnosisItem> evaluate(DiagnosisContext ctx);
}
