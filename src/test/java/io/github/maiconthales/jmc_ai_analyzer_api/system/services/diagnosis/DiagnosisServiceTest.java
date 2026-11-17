package io.github.maiconthales.jmc_ai_analyzer_api.system.services.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.maiconthales.jmc_ai_analyzer_api.config.TestProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.support.ExtractionFixture;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.JmcFindingTranslator;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.TieredStopLevelRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr.JmcRuleRunner;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.CoverageResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisOutcome;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageDomain;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.FindingSeverity;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.AppFrames;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openjdk.jmc.flightrecorder.rules.IRule;

class DiagnosisServiceTest {

	private final JmcFindingTranslator translator = new JmcFindingTranslator();
	private final DiagnosisService service = new DiagnosisService(List.of(new TieredStopLevelRule()), translator,
			new CoverageService(), TestProperties.diagnosis());

	@Test
	void combinesJmcFindingsAndOwnRulesOrderedByPriorityAndScore() {
		ExtractionFixture fx = new ExtractionFixture();
		fx.jvmArgs = "-XX:TieredStopAtLevel=1";
		fx.findings.add(finding("CompareCpu", "processes", FindingSeverity.WARNING, 80.0));
		fx.findings.add(finding("Allocations.thread", "java_application", FindingSeverity.INFO, 74.0));
		fx.findings.add(finding("SomeRule", "file_io", FindingSeverity.INFO, 10.0));
		fx.findings.add(finding("Ok", "heap", FindingSeverity.OK, 1.0));

		DiagnosisOutcome outcome = service.diagnose(fx.build(), null, List.of());

		assertThat(outcome.items()).extracting(DiagnosisItem::getSource).containsExactly("JMC:CompareCpu",
				"JMC:Allocations.thread", "APP:TIERED_STOP_LEVEL", "JMC:SomeRule");
		assertThat(outcome.items().get(3).getCategory()).isEqualTo(DiagnosisCategory.IO);
		assertThat(outcome.items().get(3).getTitle()).isEqualTo("SomeRule name");
		assertThat(outcome.coverage()).hasSize(CoverageDomain.values().length);
		assertThat(outcome.healthScore()).isEqualTo(100 - 20 - 8 - 8 - 2);
	}

	@Test
	void translatorPromotesWarningsAndUsesDefaultScores() {
		var props = TestProperties.diagnosis();
		DiagnosisItem warning = translator.toItem(finding("GcInvertedParallelism", "garbage_collection",
				FindingSeverity.WARNING, null), props);
		assertThat(warning.getPriority()).isEqualTo(Priority.MEDIA);
		assertThat(warning.getScore()).isEqualTo(50);
		assertThat(warning.getCategory()).isEqualTo(DiagnosisCategory.GC);
		RuleFinding noSolution = finding("Other", "unknown_topic", FindingSeverity.INFO, 30.0);
		noSolution.setSolution(null);
		DiagnosisItem item = translator.toItem(noSolution, props);
		assertThat(item.getCategory()).isEqualTo(DiagnosisCategory.AMBIENTE);
		assertThat(item.getAction()).isEqualTo("explicação");
	}

	@Test
	void everyTranslatedRuleExistsInJmcAndCatalogIsReviewedWhenJmcChanges() {
		Set<String> ids = JmcRuleRunner.rules().stream().map(IRule::getId).collect(Collectors.toSet());
		assertThat(ids).containsAll(JmcFindingTranslator.TRANSLATIONS.keySet());
		assertThat(ids).as("JMC 9.1.2 tem 68 regras; ao atualizar o JMC, revise as traduções").hasSize(68);
		Set<String> topics = JmcRuleRunner.rules().stream().map(IRule::getTopic).collect(Collectors.toSet());
		assertThat(JmcFindingTranslator.TOPICS.keySet()).allSatisfy(t -> assertThat(topics).contains(t));
	}

	@Test
	void coverageMarksAbsentInsufficientAndOk() {
		ExtractionFixture fx = new ExtractionFixture();
		fx.durationMs = 60_000;
		fx.eventCounts.putAll(Map.of(JfrEventTypes.CPU_LOAD, 60L, JfrEventTypes.EXECUTION_SAMPLE, 220L,
				JfrEventTypes.SOCKET_READ, 3L));
		fx.settings.put(JfrEventTypes.EXCEPTION_THROW, Map.of("enabled", "false"));
		fx.settings.put(JfrEventTypes.FILE_READ, Map.of("enabled", "true", "threshold", "10 ms"));
		fx.settings.put(JfrEventTypes.HEAP_SUMMARY, Map.of("enabled", "true"));

		CoverageResult coverage = new CoverageService().evaluate(fx.build(), TestProperties.diagnosis());
		Map<CoverageDomain, CoverageStatus> status = coverage.items().stream()
				.collect(Collectors.toMap(c -> c.getDomain(), c -> c.getStatus()));

		assertThat(status).containsEntry(CoverageDomain.CPU, CoverageStatus.OK)
				.containsEntry(CoverageDomain.SOCKET_IO, CoverageStatus.OK)
				.containsEntry(CoverageDomain.HOT_METHODS, CoverageStatus.INSUFICIENTE)
				.containsEntry(CoverageDomain.EXCEPTIONS, CoverageStatus.AUSENTE)
				.containsEntry(CoverageDomain.FILE_IO, CoverageStatus.AUSENTE);
		assertThat(reason(coverage, CoverageDomain.EXCEPTIONS)).contains("desabilitado");
		assertThat(reason(coverage, CoverageDomain.FILE_IO)).contains("limiar 10 ms");
		assertThat(reason(coverage, CoverageDomain.HEAP)).contains("habilitado, mas sem ocorrências");
		assertThat(reason(coverage, CoverageDomain.GC)).startsWith("Nenhum evento");
		assertThat(reason(coverage, CoverageDomain.HOT_METHODS)).contains("220 amostras", "60 s");
		assertThat(coverage.suggestedCommand()).contains("jdk.JavaExceptionThrow#enabled=true",
				"jdk.FileRead#threshold=1ms", "jdk.JavaMonitorEnter#threshold=10ms");
	}

	@Test
	void coverageWithEnoughSamplesIsOkAndCommandHasNoExtras() {
		ExtractionFixture fx = new ExtractionFixture();
		CoverageService.DOMAINS.values().forEach(d -> d.eventTypes().forEach(t -> fx.eventCounts.put(t, 5000L)));
		CoverageResult coverage = new CoverageService().evaluate(fx.build(), TestProperties.diagnosis());
		assertThat(coverage.items()).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(CoverageStatus.OK));
		assertThat(coverage.suggestedCommand()).isEqualTo(CoverageService.BASE_COMMAND);
	}

	@Test
	void appFramesUseAppPackagesOrSkipFrameworks() {
		List<StackFrame> frames = List.of(new StackFrame(null, "x", null, null),
				new StackFrame("java.lang.String", "valueOf", "()V", 1),
				new StackFrame("org.springframework.X", "y", "()V", 2), new StackFrame("com.app.A", "b", "()V", 3));
		List<String> framework = TestProperties.diagnosis().frameworkPackages();
		assertThat(AppFrames.resolve(frames, List.of(), framework).c()).isEqualTo("com.app.A");
		assertThat(AppFrames.resolve(frames, List.of("org.springframework"), framework).c())
				.isEqualTo("org.springframework.X");
		assertThat(AppFrames.resolve(frames, List.of("nao.existe"), framework)).isNull();
		assertThat(AppFrames.resolve(List.of(), List.of(), framework)).isNull();
		assertThat(AppFrames.resolve(null, List.of(), framework)).isNull();
	}

	@Test
	void priorityFromScoreAndHelpers() {
		assertThat(Priority.fromScore(null)).isEqualTo(Priority.BAIXA);
		assertThat(Priority.fromScore(10.0)).isEqualTo(Priority.BAIXA);
		assertThat(Priority.fromScore(25.0)).isEqualTo(Priority.MEDIA);
		assertThat(Priority.fromScore(75.0)).isEqualTo(Priority.ALTA);
		assertThat(Diagnoses.bytes(512)).isEqualTo("512.0 B");
		assertThat(Diagnoses.bytes(3.0 * (1L << 40) * 2000)).endsWith("TB");
		assertThat(Diagnoses.frames(List.of())).isNull();
		assertThat(Diagnoses.defaultScore(Priority.BAIXA, TestProperties.diagnosis())).isEqualTo(15);
		assertThat(Diagnoses.item(Priority.ALTA, DiagnosisCategory.GC, "s", 150, "t", null, null, null, null).getScore())
				.isEqualTo(100);
	}

	private static String reason(CoverageResult coverage, CoverageDomain domain) {
		return coverage.items().stream().filter(c -> c.getDomain() == domain).findFirst().orElseThrow().getReason();
	}

	private static RuleFinding finding(String id, String topic, FindingSeverity severity, Double score) {
		RuleFinding f = new RuleFinding();
		f.setRuleId(id);
		f.setRuleName(id + " name");
		f.setTopic(topic);
		f.setSeverity(severity);
		f.setScore(score);
		f.setSummary("resumo");
		f.setExplanation("explicação");
		f.setSolution("solução");
		return f;
	}
}
