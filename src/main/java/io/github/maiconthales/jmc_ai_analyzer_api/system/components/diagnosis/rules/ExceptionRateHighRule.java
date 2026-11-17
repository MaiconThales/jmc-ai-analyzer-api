package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Muitas exceções por segundo (exceção usada como controle de fluxo). */
@Component
public class ExceptionRateHighRule implements DiagnosisRule {

	@Override
	public String id() {
		return "EXCEPTION_RATE_HIGH";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var exceptions = ctx.extraction().exceptions();
		double seconds = ctx.extraction().metadata().durationSeconds();
		if (exceptions.throwablesTotal() == null || seconds <= 0) {
			return Optional.empty();
		}
		double rate = exceptions.throwablesTotal() / seconds;
		if (rate <= ctx.properties().exceptionRatePerSecond()) {
			return Optional.empty();
		}
		var top = exceptions.top().isEmpty() ? null : exceptions.top().get(0);
		return Optional.of(Diagnoses.app(this, Priority.MEDIA, DiagnosisCategory.EXCECOES, ctx.properties(),
				"Muitas exceções por segundo",
				Diagnoses.number(rate) + " throwables/s (" + exceptions.throwablesTotal() + " no total)"
						+ (top == null ? "." : "; a mais frequente é " + top.getExceptionClass() + "."),
				top == null ? null : Diagnoses.frames(top.getStackFrames()),
				"Evite usar exceções como controle de fluxo; valide antes de lançar e trate a causa da exceção mais "
						+ "frequente.",
				"exceptions"));
	}
}
