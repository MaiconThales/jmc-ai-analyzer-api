package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Pico de uso do heap acima do limite em relação ao heap máximo. */
@Component
public class HeapPressureRule implements DiagnosisRule {

	@Override
	public String id() {
		return "HEAP_PRESSURE";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var heap = ctx.extraction().heap();
		if (!heap.available() || heap.maxBytes() == null || heap.maxBytes() <= 0 || heap.usedPeakBytes() == null) {
			return Optional.empty();
		}
		double pct = heap.usedPeakBytes() * 100.0 / heap.maxBytes();
		if (pct <= ctx.properties().heapPressurePct()) {
			return Optional.empty();
		}
		return Optional.of(Diagnoses.app(this, Priority.ALTA, DiagnosisCategory.MEMORIA, ctx.properties(),
				"Heap próximo do limite",
				"O pico de uso do heap foi " + Diagnoses.bytes(heap.usedPeakBytes()) + " (" + Diagnoses.number(pct)
						+ "% do máximo de " + Diagnoses.bytes(heap.maxBytes()) + ").",
				null,
				"Investigue retenção de objetos (veja os suspeitos de vazamento) ou aumente -Xmx se o volume for esperado.",
				"heap"));
	}
}
