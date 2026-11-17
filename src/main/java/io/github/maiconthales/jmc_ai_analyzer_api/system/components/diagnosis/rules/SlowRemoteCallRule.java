package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisLocation;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Leitura de socket lenta: dependência remota respondendo devagar. */
@Component
public class SlowRemoteCallRule implements DiagnosisRule {

	@Override
	public String id() {
		return "SLOW_REMOTE_CALL";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		if (!ctx.extraction().io().available()) {
			return Optional.empty();
		}
		return ctx.extraction().io().top().stream()
				.filter(i -> i.getIoType() == IoType.SOCKET_READ && i.getMaxMs() > ctx.properties().slowSocketReadMs())
				.max(Comparator.comparingDouble(IoHotspot::getMaxMs))
				.map(i -> Diagnoses.app(this, Priority.ALTA, DiagnosisCategory.IO, ctx.properties(),
						"Dependência remota lenta",
						"Leitura de " + i.getTarget() + " levou até " + Diagnoses.number(i.getMaxMs()) + " ms ("
								+ i.getEventCount() + " leituras lentas, " + Diagnoses.number(i.getTotalMs())
								+ " ms no total).",
						new DiagnosisLocation(null, i.getTarget(), null),
						"Verifique a dependência indicada (consultas lentas, rede, pool de conexões) e configure "
								+ "timeouts adequados.",
						"io"));
	}
}
