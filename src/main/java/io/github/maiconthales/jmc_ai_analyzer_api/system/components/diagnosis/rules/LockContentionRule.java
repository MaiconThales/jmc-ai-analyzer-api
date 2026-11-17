package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.LockType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tempo de espera para entrar num monitor ({@code synchronized}) acima do limite. */
@Component
public class LockContentionRule implements DiagnosisRule {

	@Override
	public String id() {
		return "LOCK_CONTENTION";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		long durationMs = ctx.extraction().metadata().durationMs();
		if (!ctx.extraction().locks().available() || durationMs <= 0) {
			return Optional.empty();
		}
		double limit = durationMs * ctx.properties().lockTimePct() / 100;
		return ctx.extraction().locks().top().stream()
				.filter(l -> l.getLockType() == LockType.MONITOR_ENTER && l.getTotalMs() > limit)
				.max(Comparator.comparingDouble(LockContention::getTotalMs))
				.map(l -> Diagnoses.app(this, Priority.ALTA, DiagnosisCategory.LOCKS, ctx.properties(),
						"Contenção de lock",
						"Threads esperaram " + Diagnoses.number(l.getTotalMs()) + " ms para entrar no monitor de "
								+ l.getClassName() + " (" + l.getEventCount() + " vezes; máximo de "
								+ Diagnoses.number(l.getMaxMs()) + " ms).",
						Diagnoses.frames(l.getStackFrames()),
						"Reduza o trecho dentro do synchronized, divida o lock ou troque por estruturas concorrentes.",
						"locks"));
	}
}
