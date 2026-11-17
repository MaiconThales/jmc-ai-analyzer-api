package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** A gravação não permite diagnosticar todos os domínios. */
@Component
public class RecordingCoverageRule implements DiagnosisRule {

	@Override
	public String id() {
		return "RECORDING_COVERAGE";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		String missing = ctx.coverage().stream()
				.filter(c -> c.getStatus() != CoverageStatus.OK)
				.map(c -> c.getDomain() + " (" + c.getStatus() + ")")
				.collect(Collectors.joining(", "));
		if (missing.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(Diagnoses.app(this, Priority.BAIXA, DiagnosisCategory.GRAVACAO, ctx.properties(),
				"Gravação com dados incompletos",
				"Domínios sem dados suficientes: " + missing + ".",
				null,
				"Grave novamente seguindo as recomendações da cobertura e o comando sugerido.",
				"coverage"));
	}
}
