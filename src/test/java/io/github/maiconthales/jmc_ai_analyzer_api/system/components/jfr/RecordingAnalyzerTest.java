package io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.maiconthales.jmc_ai_analyzer_api.config.TestProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.support.JfrFixtures;
import io.github.maiconthales.jmc_ai_analyzer_api.support.SyntheticRecordings;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingMetadata;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.FindingSeverity;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.LockType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.AnalysisInterruptedException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.JfrParseException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Extração completa sobre as gravações reais e uma sintética (domínios que faltam nos exemplos). */
class RecordingAnalyzerTest {

	private static final RecordingAnalyzer ANALYZER = new RecordingAnalyzer(TestProperties.analysis(),
			new JmcRuleRunner(TestProperties.analysis()));

	private static RecordingExtraction example;

	@TempDir
	Path tmp;

	@BeforeAll
	static void analyzeExample() {
		example = ANALYZER.analyze(JfrFixtures.example(0));
	}

	@Test
	void metadataComesFromJvmOsAndCpuEvents() {
		RecordingMetadata m = example.metadata();
		assertThat(m.durationMs()).isBetween(59_000L, 61_000L);
		assertThat(m.eventCount()).isPositive();
		assertThat(m.jvmName()).contains("HotSpot");
		assertThat(m.jvmArgs()).contains("-XX:ActiveProcessorCount=1");
		assertThat(m.osName()).startsWith("OS:").doesNotContain("\n");
		assertThat(m.cpuCores()).isEqualTo(6);
		assertThat(m.hardwareThreads()).isEqualTo(12);
		assertThat(m.physicalMemoryBytes()).isPositive();
	}

	@Test
	void periodicDomainsHaveSummariesAndSeries() {
		assertThat(example.cpu().available()).isTrue();
		assertThat(example.cpu().jvmMax()).isGreaterThanOrEqualTo(example.cpu().jvmAvg());
		assertThat(example.cpu().series()).extracting(MetricSample::getMetric)
				.contains(MetricType.CPU_JVM_USER, MetricType.CPU_JVM_SYSTEM, MetricType.CPU_MACHINE);
		assertThat(example.heap().available()).isTrue();
		assertThat(example.heap().maxBytes()).isEqualTo(805306368L);
		assertThat(example.heap().youngCollector()).isEqualTo("G1New");
		assertThat(example.heap().parallelGcThreads()).isEqualTo(1L);
		assertThat(example.gc().count()).isEqualTo(24);
		assertThat(example.gc().pauseMaxMs()).isGreaterThanOrEqualTo(example.gc().pauseP95Ms());
		assertThat(example.gc().topPauses()).isNotEmpty().first().satisfies(p -> assertThat(p.getRank()).isEqualTo(1));
		assertThat(example.threads().peak()).isPositive();
		assertThat(example.threads().activeValues()).isNotEmpty();
		assertThat(example.threadCpu().top()).first()
				.satisfies(t -> assertThat(t.getThreadName()).contains("KafkaListenerEndpointContainer"));
	}

	@Test
	void sampledDomainsAreRankedWithStacks() {
		assertThat(example.hotMethods().totalSamples()).isEqualTo(220);
		List<HotMethod> hot = example.hotMethods().top();
		assertThat(hot).isSortedAccordingTo((a, b) -> Long.compare(b.getSamples(), a.getSamples()));
		assertThat(hot.get(0).getStackFrames()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
		assertThat(example.allocation().rateBytesPerSecond()).isPositive();
		assertThat(example.allocation().top().get(0).getClassName()).isEqualTo("java.lang.String");
		assertThat(example.locks().top()).extracting(LockContention::getLockType).contains(LockType.THREAD_PARK,
				LockType.MONITOR_WAIT);
		assertThat(example.io().top()).first().satisfies(i -> {
			assertThat(i.getIoType()).isEqualTo(IoType.SOCKET_READ);
			assertThat(i.getTarget()).endsWith(":1433");
		});
		assertThat(example.io().totals()).containsKey(IoType.SOCKET_READ);
		assertThat(example.leaks().top()).isNotEmpty();
		assertThat(example.exceptions().throwablesTotal()).isNotNull();
	}

	@Test
	void jmcFindingsHavePopulatedMessagesAndNotApplicableRulesAreKept() {
		assertThat(example.rules().findings()).extracting(RuleFinding::getRuleId).contains("Allocations.class");
		assertThat(example.rules().findings()).allSatisfy(f -> {
			assertThat(f.getSeverity()).isIn(FindingSeverity.OK, FindingSeverity.INFO, FindingSeverity.WARNING);
			assertThat(String.valueOf(f.getSummary())).doesNotContain("{");
		});
		assertThat(example.rules().notApplicable()).isNotEmpty();
		assertThat(example.settings().enabled(JfrEventTypes.EXCEPTION_THROW)).contains(false);
		assertThat(example.eventCount(JfrEventTypes.EXECUTION_SAMPLE)).isEqualTo(220);
	}

	@Test
	void sameFileProducesSameResult() {
		RecordingExtraction again = ANALYZER.analyze(JfrFixtures.example(0));
		assertThat(again.rules().findings()).extracting(RuleFinding::getSummary)
				.containsExactlyElementsOf(example.rules().findings().stream().map(RuleFinding::getSummary).toList());
		assertThat(again.hotMethods().top()).extracting(HotMethod::getMethodSignature).containsExactlyElementsOf(
				example.hotMethods().top().stream().map(HotMethod::getMethodSignature).toList());
		assertThat(again.gc().pauseTotalMs()).isEqualTo(example.gc().pauseTotalMs());
	}

	@Test
	void syntheticRecordingCoversExceptionsFileIoAndMonitorContention() {
		RecordingExtraction x = ANALYZER.analyze(SyntheticRecordings.withExceptionsIoAndLocks(tmp));
		assertThat(x.exceptions().top()).anySatisfy(e -> {
			assertThat(e.getExceptionClass()).isEqualTo("java.lang.IllegalArgumentException");
			assertThat(e.getSampleMessage()).startsWith("falha sintética");
		});
		assertThat(x.io().totals()).containsKeys(IoType.FILE_READ, IoType.FILE_WRITE);
		assertThat(x.locks().top()).anySatisfy(l -> assertThat(l.getLockType()).isEqualTo(LockType.MONITOR_ENTER));
		assertThat(x.gc().available()).isFalse();
		assertThat(x.heap().available()).isFalse();
		assertThat(x.leaks().available()).isFalse();
		assertThat(x.threadCpu().available()).isFalse();
		assertThat(x.threads().available()).isFalse();
	}

	@Test
	void unreadableFilesFailWithParseError() {
		assertThatThrownBy(() -> ANALYZER.analyze(JfrFixtures.truncated(tmp))).isInstanceOf(JfrParseException.class);
		assertThatThrownBy(() -> ANALYZER.analyze(JfrFixtures.badMagic(tmp))).isInstanceOf(JfrParseException.class);
		assertThatThrownBy(() -> ANALYZER.analyze(tmp.resolve("missing.jfr"))).isInstanceOf(JfrParseException.class);
	}

	@Test
	void interruptedThreadStopsBetweenSteps() {
		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(() -> ANALYZER.analyze(JfrFixtures.example(1)))
					.isInstanceOfAny(AnalysisInterruptedException.class, JfrParseException.class);
		} finally {
			Thread.interrupted();
		}
	}
}
