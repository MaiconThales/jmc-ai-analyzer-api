package io.github.maiconthales.jmc_ai_analyzer_api.support;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.DomainResults;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.JmcRulesResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingMetadata;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingSettings;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.AllocationHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.ExceptionHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.HotMethod;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.IoHotspot;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.LockContention;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.IoType;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.LockType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Construtor mutável de {@link RecordingExtraction} para testes de regras; o padrão não dispara nenhuma regra. */
public class ExtractionFixture {

	public static final List<StackFrame> APP_FRAMES = List.of(new StackFrame("java.lang.String", "valueOf", "()V", 1),
			new StackFrame("com.empresa.Service", "run", "()V", 42));

	public String jvmArgs = "-Xms1g -Xmx1g";
	public String javaArgs = "app-1.2.3.jar";
	public Integer cores = 4;
	public Integer hwThreads = 8;
	public long durationMs = 600_000;
	public DomainResults.Cpu cpu = new DomainResults.Cpu(true, 0.2, 0.4, 0.3, 0.5, List.of(), List.of(0.3, 0.4));
	public DomainResults.Heap heap = new DomainResults.Heap(true, 1L << 30, 1L << 30, 1L << 29, "G1New", "G1Old", 4L,
			List.of());
	public DomainResults.Gc gc = new DomainResults.Gc(true, 10, 100, 10, 20, 30, 0.1, List.of());
	public DomainResults.Threads threads = new DomainResults.Threads(true, 20, 10, 5, 5, List.of(),
			List.of(20.0, 19.0, 20.0));
	public List<LockContention> locks = new ArrayList<>();
	public DomainResults.HotMethods hotMethods = new DomainResults.HotMethods(true, 2000, List.of());
	public DomainResults.Allocation allocation = new DomainResults.Allocation(true, 1e6, List.of());
	public DomainResults.Exceptions exceptions = new DomainResults.Exceptions(true, 10L, List.of());
	public List<IoHotspot> io = new ArrayList<>();
	public List<RuleFinding> findings = new ArrayList<>();
	public Map<String, Long> eventCounts = new HashMap<>();
	public Map<String, Map<String, String>> settings = new HashMap<>();

	public RecordingExtraction build() {
		RecordingMetadata metadata = new RecordingMetadata(Instant.EPOCH, Instant.EPOCH.plusMillis(durationMs),
				durationMs, 1000, "HotSpot", "21", jvmArgs, javaArgs, "Linux", cores, hwThreads, 1L << 34);
		return new RecordingExtraction(metadata, cpu, heap, gc, threads,
				new DomainResults.Locks(!locks.isEmpty(), locks), hotMethods, allocation, exceptions,
				new DomainResults.Io(!io.isEmpty(), Map.of(), io), new DomainResults.Leaks(false, List.of()),
				new DomainResults.ThreadCpuLoad(false, List.of()), new JmcRulesResult(findings, List.of()),
				new RecordingSettings(settings), eventCounts);
	}

	public static HotMethod hotMethod(double percent, List<StackFrame> frames) {
		HotMethod m = new HotMethod();
		m.setMethodSignature("com.empresa.Service.run()");
		m.setSamples(500);
		m.setPercent(percent);
		m.setStackFrames(frames);
		return m;
	}

	public static LockContention lock(LockType type, double totalMs) {
		LockContention l = new LockContention();
		l.setLockType(type);
		l.setClassName("com.empresa.Cache");
		l.setEventCount(10);
		l.setTotalMs(totalMs);
		l.setMaxMs(totalMs / 2);
		l.setStackFrames(APP_FRAMES);
		return l;
	}

	public static IoHotspot socket(String target, double maxMs) {
		IoHotspot i = new IoHotspot();
		i.setIoType(IoType.SOCKET_READ);
		i.setTarget(target);
		i.setEventCount(3);
		i.setTotalMs(maxMs * 2);
		i.setMaxMs(maxMs);
		return i;
	}

	public static AllocationHotspot allocation(String className) {
		AllocationHotspot a = new AllocationHotspot();
		a.setClassName(className);
		a.setWeightBytes(1000);
		a.setSamples(10);
		a.setStackFrames(APP_FRAMES);
		return a;
	}

	public static ExceptionHotspot exception(String className) {
		ExceptionHotspot e = new ExceptionHotspot();
		e.setExceptionClass(className);
		e.setEventCount(100);
		e.setStackFrames(APP_FRAMES);
		return e;
	}
}
