package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JvmArgsParser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@code -XX:ActiveProcessorCount} menor que os processadores da máquina. */
@Component
public class ActiveProcessorCountRule implements DiagnosisRule {

	static final String FLAG = "ActiveProcessorCount";

	@Override
	public String id() {
		return "ACTIVE_PROCESSOR_COUNT";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var metadata = ctx.extraction().metadata();
		Integer real = metadata.hardwareThreads() != null ? metadata.hardwareThreads() : metadata.cpuCores();
		Optional<Integer> configured = JvmArgsParser.xxValue(metadata.jvmArgs(), FLAG).flatMap(v -> {
			try {
				return Optional.of(Integer.parseInt(v));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		});
		if (real == null || configured.isEmpty() || configured.get() >= real) {
			return Optional.empty();
		}
		return Optional.of(Diagnoses.app(this, Priority.MEDIA, DiagnosisCategory.CONFIG_JVM, ctx.properties(),
				"JVM limitada a menos processadores que a máquina",
				"-XX:" + FLAG + "=" + configured.get() + " com " + real
						+ " processadores disponíveis: GC, JIT e pools usam menos threads.",
				null,
				"Remova -XX:" + FLAG + " ou ajuste para a quantidade real de processadores destinada à aplicação.",
				"metadata"));
	}
}
