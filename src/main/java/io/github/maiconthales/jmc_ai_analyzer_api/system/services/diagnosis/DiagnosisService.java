package io.github.maiconthales.jmc_ai_analyzer_api.system.services.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.JmcFindingTranslator;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.CoverageResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisOutcome;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.HealthScoreCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Gera o diagnóstico priorizado, a cobertura e a nota de saúde de uma gravação. */
@Service
@RequiredArgsConstructor
public class DiagnosisService {

	/** Ordem de exibição: prioridade e depois score decrescente. */
	public static final Comparator<DiagnosisItem> ORDER = Comparator.comparing(DiagnosisItem::getPriority)
			.thenComparing(Comparator.comparingInt(DiagnosisItem::getScore).reversed())
			.thenComparing(DiagnosisItem::getSource);

	private final List<DiagnosisRule> rules;
	private final JmcFindingTranslator translator;
	private final CoverageService coverageService;
	private final DiagnosisProperties properties;


	public DiagnosisOutcome diagnose(RecordingExtraction extraction, Environment environment, List<String> appPackages) {
		CoverageResult coverage = coverageService.evaluate(extraction, properties);
		DiagnosisContext ctx = new DiagnosisContext(extraction, environment, appPackages, coverage.items(), properties);
		List<DiagnosisItem> items = new ArrayList<>();
		extraction.rules().findings().stream()
				.filter(translator::isDiagnosable)
				.map(f -> translator.toItem(f, properties))
				.forEach(items::add);
		for (DiagnosisRule rule : rules) {
			rule.evaluate(ctx).ifPresent(items::add);
		}
		items.sort(ORDER);
		return new DiagnosisOutcome(items, coverage.items(), HealthScoreCalculator.calculate(items, properties),
				coverage.suggestedCommand());
	}
}
