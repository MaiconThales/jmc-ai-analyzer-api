package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Taxa de alocação acima do limite. */
@Component
public class AllocationRateHighRule implements DiagnosisRule {

	@Override
	public String id() {
		return "ALLOCATION_RATE_HIGH";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var allocation = ctx.extraction().allocation();
		Double rate = allocation.rateBytesPerSecond();
		if (!allocation.available() || rate == null || rate <= ctx.properties().allocationRate().toBytes()) {
			return Optional.empty();
		}
		var top = allocation.top().isEmpty() ? null : allocation.top().get(0);
		String evidence = "Taxa de alocação de " + Diagnoses.bytes(rate) + "/s"
				+ (top == null ? "." : "; o tipo mais alocado é " + top.getClassName() + ".");
		return Optional.of(Diagnoses.app(this, Priority.MEDIA, DiagnosisCategory.MEMORIA, ctx.properties(),
				"Taxa de alocação alta", evidence,
				top == null ? null : Diagnoses.frames(top.getStackFrames()),
				"Reduza alocações temporárias no ponto indicado (reuso de buffers, menos conversões para String, "
						+ "evitar boxing).",
				"allocation"));
	}
}
