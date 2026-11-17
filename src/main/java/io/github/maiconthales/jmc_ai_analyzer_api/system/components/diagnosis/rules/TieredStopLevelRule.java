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

/** {@code -XX:TieredStopAtLevel} abaixo de 4 desliga o compilador C2. */
@Component
public class TieredStopLevelRule implements DiagnosisRule {

	static final String FLAG = "TieredStopAtLevel";
	static final int FULL_TIERED = 4;

	@Override
	public String id() {
		return "TIERED_STOP_LEVEL";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		String args = ctx.extraction().metadata().jvmArgs();
		Optional<Integer> level = JvmArgsParser.xxValue(args, FLAG).flatMap(TieredStopLevelRule::parse);
		if (level.isEmpty() || level.get() >= FULL_TIERED) {
			return Optional.empty();
		}
		int occurrences = JvmArgsParser.xxCount(args, FLAG);
		String evidence = "-XX:" + FLAG + "=" + level.get() + " está ativo"
				+ (occurrences > 1 ? " (a flag aparece " + occurrences + " vezes; vale a última)." : ".")
				+ " Só o compilador C1 é usado, e o código fica mais lento.";
		return Optional.of(Diagnoses.app(this, Priority.MEDIA, DiagnosisCategory.CONFIG_JVM, ctx.properties(),
				"Flag da JVM limita o compilador ao C1", evidence, null,
				"Remova -XX:" + FLAG + " (configuração de IDE para subir rápido); em produção, não use a flag.",
				"metadata"));
	}

	private static Optional<Integer> parse(String value) {
		try {
			return Optional.of(Integer.parseInt(value));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
