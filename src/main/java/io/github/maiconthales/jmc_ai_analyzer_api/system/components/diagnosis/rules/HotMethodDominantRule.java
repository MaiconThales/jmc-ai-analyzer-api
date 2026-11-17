package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.AppFrames;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Um método da aplicação concentra uma fatia grande das amostras de CPU. */
@Component
public class HotMethodDominantRule implements DiagnosisRule {

	@Override
	public String id() {
		return "HOT_METHOD_DOMINANT";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var hot = ctx.extraction().hotMethods();
		if (!hot.available()) {
			return Optional.empty();
		}
		return hot.top().stream()
				.filter(m -> m.getPercent() > ctx.properties().hotMethodPct())
				.filter(m -> AppFrames.resolve(m.getStackFrames(), ctx.appPackages(),
						ctx.properties().frameworkPackages()) != null)
				.findFirst()
				.map(m -> Diagnoses.app(this, Priority.MEDIA, DiagnosisCategory.CPU, ctx.properties(),
						"Método concentra o uso de CPU",
						m.getMethodSignature() + " aparece em " + Diagnoses.number(m.getPercent()) + "% das "
								+ hot.totalSamples() + " amostras de CPU.",
						Diagnoses.frames(m.getStackFrames()),
						"Otimize o método indicado (algoritmo, laços, chamadas repetidas) ou reduza quantas vezes ele "
								+ "é chamado.",
						"hotMethods"));
	}
}
