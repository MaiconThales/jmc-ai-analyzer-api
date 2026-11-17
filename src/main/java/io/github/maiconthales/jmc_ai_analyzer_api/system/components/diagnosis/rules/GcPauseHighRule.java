package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Pausas de GC longas ou tempo excessivo em pausa. */
@Component
public class GcPauseHighRule implements DiagnosisRule {

	@Override
	public String id() {
		return "GC_PAUSE_HIGH";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var gc = ctx.extraction().gc();
		var props = ctx.properties();
		if (!gc.available() || (gc.pauseMaxMs() <= props.gcPauseMaxMs() && gc.pausePct() <= props.gcPausePct())) {
			return Optional.empty();
		}
		return Optional.of(Diagnoses.app(this, Priority.ALTA, DiagnosisCategory.GC, props,
				"Pausas de GC longas",
				"Pausa máxima de " + Diagnoses.number(gc.pauseMaxMs()) + " ms; " + Diagnoses.number(gc.pausePct())
						+ "% do tempo da gravação em pausa (" + gc.count() + " coletas).",
				null,
				"Revise o tamanho do heap, o coletor e a taxa de alocação; reduza objetos de vida curta nos pontos mais "
						+ "alocadores.",
				"gc"));
	}
}
