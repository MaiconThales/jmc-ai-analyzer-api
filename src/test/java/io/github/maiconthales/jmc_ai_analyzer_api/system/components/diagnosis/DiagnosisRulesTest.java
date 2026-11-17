package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.maiconthales.jmc_ai_analyzer_api.config.TestProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.support.ExtractionFixture;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.ActiveProcessorCountRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.AllocationRateHighRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.CpuSaturationRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.ExceptionRateHighRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.GcPauseHighRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.HeapPressureRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.HotMethodDominantRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.LockContentionRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.RecordingCoverageRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.SlowRemoteCallRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.ThreadGrowthRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.TieredStopLevelRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules.XmsXmxMismatchRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.CoverageItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageDomain;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.CoverageStatus;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Environment;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.LockType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Cada regra do catálogo: um cenário em que dispara e outros em que não dispara. */
class DiagnosisRulesTest {

	private static final List<DiagnosisRule> ALL = List.of(new HeapPressureRule(), new GcPauseHighRule(),
			new AllocationRateHighRule(), new CpuSaturationRule(), new HotMethodDominantRule(),
			new LockContentionRule(), new ExceptionRateHighRule(), new SlowRemoteCallRule(), new ThreadGrowthRule(),
			new TieredStopLevelRule(), new ActiveProcessorCountRule(), new XmsXmxMismatchRule(),
			new RecordingCoverageRule());

	private final ExtractionFixture fx = new ExtractionFixture();

	@Test
	void healthyRecordingTriggersNothing() {
		DiagnosisContext ctx = ctx(Environment.PROD, List.of());
		assertThat(ALL).allSatisfy(rule -> assertThat(rule.evaluate(ctx)).as(rule.id()).isEmpty());
	}

	@Test
	void unavailableDomainsTriggerNothing() {
		fx.cpu = DomainResults.Cpu.empty();
		fx.heap = new DomainResults.Heap(false, null, null, null, null, null, null, List.of());
		fx.gc = DomainResults.Gc.empty();
		fx.threads = new DomainResults.Threads(false, null, null, 0, 0, List.of(), List.of());
		fx.hotMethods = new DomainResults.HotMethods(false, 0, List.of());
		fx.allocation = new DomainResults.Allocation(false, null, List.of());
		fx.exceptions = new DomainResults.Exceptions(false, null, List.of());
		fx.jvmArgs = null;
		fx.cores = null;
		fx.hwThreads = null;
		DiagnosisContext ctx = ctx(null, List.of());
		assertThat(ALL).allSatisfy(rule -> assertThat(rule.evaluate(ctx)).as(rule.id()).isEmpty());
	}

	@Test
	void heapPressure() {
		fx.heap = new DomainResults.Heap(true, 1L, 1000L, 900L, null, null, null, List.of());
		assertItem(new HeapPressureRule(), Priority.ALTA).satisfies(i -> assertThat(i.getEvidence()).contains("90.0%"));
		fx.heap = new DomainResults.Heap(true, 1L, 0L, 900L, null, null, null, List.of());
		assertThat(new HeapPressureRule().evaluate(ctx(null, List.of()))).isEmpty();
	}

	@Test
	void gcPauseHigh() {
		fx.gc = new DomainResults.Gc(true, 3, 900, 300, 400, 500, 0.5, List.of());
		assertItem(new GcPauseHighRule(), Priority.ALTA);
		fx.gc = new DomainResults.Gc(true, 3, 900, 30, 40, 50, 9, List.of());
		assertItem(new GcPauseHighRule(), Priority.ALTA);
	}

	@Test
	void allocationRateHigh() {
		fx.allocation = new DomainResults.Allocation(true, 3e9, List.of(ExtractionFixture.allocation("byte[]")));
		assertItem(new AllocationRateHighRule(), Priority.MEDIA)
				.satisfies(i -> assertThat(i.getLocation().stackFrames()).isEqualTo(ExtractionFixture.APP_FRAMES));
		fx.allocation = new DomainResults.Allocation(true, 3e9, List.of());
		assertItem(new AllocationRateHighRule(), Priority.MEDIA).satisfies(i -> assertThat(i.getLocation()).isNull());
	}

	@Test
	void cpuSaturation() {
		fx.cpu = new DomainResults.Cpu(true, 0.1, 0.2, 0.95, 1.0, List.of(), List.of(0.95, 0.97, 0.5));
		assertItem(new CpuSaturationRule(), Priority.ALTA);
		fx.cpu = new DomainResults.Cpu(true, 0.1, 0.2, 0.95, 1.0, List.of(), List.of());
		assertThat(new CpuSaturationRule().evaluate(ctx(null, List.of()))).isEmpty();
	}

	@Test
	void hotMethodDominantRequiresApplicationFrame() {
		fx.hotMethods = new DomainResults.HotMethods(true, 2000,
				List.of(ExtractionFixture.hotMethod(40, List.of(new StackFrame("java.util.HashMap", "get", "()V", 1))),
						ExtractionFixture.hotMethod(30, ExtractionFixture.APP_FRAMES)));
		assertItem(new HotMethodDominantRule(), Priority.MEDIA)
				.satisfies(i -> assertThat(i.getEvidence()).contains("30.0%"));
		assertThat(new HotMethodDominantRule().evaluate(ctx(null, List.of("org.outra")))).isEmpty();
	}

	@Test
	void lockContentionConsidersOnlyMonitorEnter() {
		fx.locks.add(ExtractionFixture.lock(LockType.THREAD_PARK, 500_000));
		assertThat(new LockContentionRule().evaluate(ctx(null, List.of()))).isEmpty();
		fx.locks.add(ExtractionFixture.lock(LockType.MONITOR_ENTER, 10_000));
		assertItem(new LockContentionRule(), Priority.ALTA).satisfies(i -> assertThat(i.getLocation()).isNotNull());
		fx.durationMs = 0;
		assertThat(new LockContentionRule().evaluate(ctx(null, List.of()))).isEmpty();
	}

	@Test
	void exceptionRateHigh() {
		fx.durationMs = 10_000;
		fx.exceptions = new DomainResults.Exceptions(true, 5000L, List.of(ExtractionFixture.exception("java.io.IOException")));
		assertItem(new ExceptionRateHighRule(), Priority.MEDIA)
				.satisfies(i -> assertThat(i.getEvidence()).contains("java.io.IOException"));
		fx.exceptions = new DomainResults.Exceptions(true, 5000L, List.of());
		assertItem(new ExceptionRateHighRule(), Priority.MEDIA).satisfies(i -> assertThat(i.getLocation()).isNull());
	}

	@Test
	void slowRemoteCall() {
		fx.io.add(ExtractionFixture.socket("db:5432", 300));
		assertThat(new SlowRemoteCallRule().evaluate(ctx(null, List.of()))).isEmpty();
		fx.io.add(ExtractionFixture.socket("api:443", 2500));
		assertItem(new SlowRemoteCallRule(), Priority.ALTA)
				.satisfies(i -> assertThat(i.getLocation().target()).isEqualTo("api:443"));
	}

	@Test
	void threadGrowth() {
		fx.threads = new DomainResults.Threads(true, 80, 10, 100, 10, List.of(), List.of(10.0, 20.0, 40.0, 80.0));
		assertItem(new ThreadGrowthRule(), Priority.MEDIA);
		fx.threads = new DomainResults.Threads(true, 80, 10, 100, 10, List.of(), List.of(10.0, 80.0, 5.0, 3.0, 20.0));
		assertThat(new ThreadGrowthRule().evaluate(ctx(null, List.of()))).isEmpty();
		fx.threads = new DomainResults.Threads(true, 80, 10, 10, 10, List.of(), List.of(10.0, 80.0));
		assertThat(new ThreadGrowthRule().evaluate(ctx(null, List.of()))).isEmpty();
		fx.threads = new DomainResults.Threads(true, 80, 10, 10, 10, List.of(), List.of(10.0));
		assertThat(new ThreadGrowthRule().evaluate(ctx(null, List.of()))).isEmpty();
	}

	@Test
	void tieredStopLevelUsesLastOccurrence() {
		fx.jvmArgs = "-XX:TieredStopAtLevel=4 -XX:TieredStopAtLevel=1";
		assertItem(new TieredStopLevelRule(), Priority.MEDIA).satisfies(i -> assertThat(i.getEvidence()).contains("2 vezes"));
		fx.jvmArgs = "-XX:TieredStopAtLevel=1";
		assertItem(new TieredStopLevelRule(), Priority.MEDIA).satisfies(i -> assertThat(i.getEvidence()).doesNotContain("vezes"));
		fx.jvmArgs = "-XX:TieredStopAtLevel=1 -XX:TieredStopAtLevel=4";
		assertThat(new TieredStopLevelRule().evaluate(ctx(null, List.of()))).isEmpty();
		fx.jvmArgs = "-XX:TieredStopAtLevel=abc";
		assertThat(new TieredStopLevelRule().evaluate(ctx(null, List.of()))).isEmpty();
	}

	@Test
	void activeProcessorCount() {
		fx.jvmArgs = "-XX:ActiveProcessorCount=1";
		assertItem(new ActiveProcessorCountRule(), Priority.MEDIA).satisfies(i -> assertThat(i.getEvidence()).contains("8"));
		fx.hwThreads = null;
		assertItem(new ActiveProcessorCountRule(), Priority.MEDIA).satisfies(i -> assertThat(i.getEvidence()).contains("4"));
		fx.jvmArgs = "-XX:ActiveProcessorCount=x";
		assertThat(new ActiveProcessorCountRule().evaluate(ctx(null, List.of()))).isEmpty();
	}

	@Test
	void xmsXmxOnlyInProduction() {
		fx.jvmArgs = "-Xms256m -Xmx4g";
		assertThat(new XmsXmxMismatchRule().evaluate(ctx(Environment.DEV, List.of()))).isEmpty();
		assertThat(new XmsXmxMismatchRule().evaluate(ctx(Environment.PROD, List.of()))).get()
				.satisfies(i -> assertThat(i.getPriority()).isEqualTo(Priority.BAIXA));
		fx.jvmArgs = "-Xmx4g";
		assertThat(new XmsXmxMismatchRule().evaluate(ctx(Environment.PROD, List.of()))).isEmpty();
	}

	@Test
	void recordingCoverage() {
		CoverageItem missing = new CoverageItem();
		missing.setDomain(CoverageDomain.EXCEPTIONS);
		missing.setStatus(CoverageStatus.AUSENTE);
		DiagnosisContext ctx = new DiagnosisContext(fx.build(), null, List.of(), List.of(missing),
				TestProperties.diagnosis());
		assertThat(new RecordingCoverageRule().evaluate(ctx)).get()
				.satisfies(i -> assertThat(i.getEvidence()).contains("EXCEPTIONS (AUSENTE)"));
	}

	private org.assertj.core.api.ObjectAssert<DiagnosisItem> assertItem(DiagnosisRule rule, Priority priority) {
		Optional<DiagnosisItem> item = rule.evaluate(ctx(Environment.PROD, List.of()));
		assertThat(item).as(rule.id()).isPresent();
		assertThat(item.get().getSource()).isEqualTo("APP:" + rule.id());
		assertThat(item.get().getPriority()).isEqualTo(priority);
		assertThat(item.get().getTitle()).isNotBlank();
		assertThat(item.get().getAction()).isNotBlank();
		return assertThat(item.get());
	}

	private DiagnosisContext ctx(Environment environment, List<String> appPackages) {
		return new DiagnosisContext(fx.build(), environment, appPackages, List.of(), TestProperties.diagnosis());
	}
}
