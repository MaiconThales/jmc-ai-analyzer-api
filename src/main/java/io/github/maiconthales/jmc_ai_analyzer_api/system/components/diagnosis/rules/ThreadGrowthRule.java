package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Threads ativas crescendo continuamente e muito mais criadas do que encerradas. */
@Component
public class ThreadGrowthRule implements DiagnosisRule {

	@Override
	public String id() {
		return "THREAD_GROWTH";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var threads = ctx.extraction().threads();
		List<Double> active = threads.activeValues();
		if (!threads.available() || active.size() < 2) {
			return Optional.empty();
		}
		double first = active.get(0);
		double last = active.get(active.size() - 1);
		boolean growing = last > first && isMostlyNonDecreasing(active);
		boolean unbalanced = threads.started() > ctx.properties().threadGrowthRatio() * Math.max(1, threads.ended());
		if (!growing || !unbalanced) {
			return Optional.empty();
		}
		return Optional.of(Diagnoses.app(this, Priority.MEDIA, DiagnosisCategory.THREADS, ctx.properties(),
				"Crescimento contínuo de threads",
				"Threads ativas foram de " + (int) first + " para " + (int) last + "; " + threads.started()
						+ " criadas e " + threads.ended() + " encerradas.",
				null,
				"Possível vazamento de threads ou pool sem limite: limite os pools e garanta o encerramento das threads.",
				"threads"));
	}

	static boolean isMostlyNonDecreasing(List<Double> values) {
		int decreases = 0;
		for (int i = 1; i < values.size(); i++) {
			if (values.get(i) < values.get(i - 1)) {
				decreases++;
			}
		}
		return decreases * 10 <= values.size();
	}
}
