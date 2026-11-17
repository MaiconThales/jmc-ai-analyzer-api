package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JvmArgsParser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Em produção, {@code -Xms} muito menor que {@code -Xmx} causa redimensionamento do heap. */
@Component
public class XmsXmxMismatchRule implements DiagnosisRule {

	@Override
	public String id() {
		return "XMS_XMX_MISMATCH";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		if (ctx.environment() != Environment.PROD) {
			return Optional.empty();
		}
		String args = ctx.extraction().metadata().jvmArgs();
		Optional<Long> xms = JvmArgsParser.memoryFlag(args, "-Xms");
		Optional<Long> xmx = JvmArgsParser.memoryFlag(args, "-Xmx");
		if (xms.isEmpty() || xmx.isEmpty() || xms.get() >= xmx.get() * ctx.properties().xmsXmxMinRatio()) {
			return Optional.empty();
		}
		return Optional.of(Diagnoses.app(this, Priority.BAIXA, DiagnosisCategory.CONFIG_JVM, ctx.properties(),
				"-Xms bem menor que -Xmx em produção",
				"-Xms " + Diagnoses.bytes(xms.get()) + " e -Xmx " + Diagnoses.bytes(xmx.get()) + ".",
				null,
				"Use -Xms igual a -Xmx em produção para evitar redimensionamentos do heap.",
				"heap"));
	}
}
