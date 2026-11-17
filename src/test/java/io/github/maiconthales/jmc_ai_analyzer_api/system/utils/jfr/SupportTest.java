package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingSettings;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.TimedValue;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.MetricSample;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.MetricType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCStackTrace;

class SupportTest {

	@Test
	void downsamplerKeepsSmallSeriesAndBucketsLargeOnes() {
		assertThat(SeriesDownsampler.downsample(MetricType.CPU_MACHINE, List.of(), 10)).isEmpty();
		List<TimedValue> small = List.of(new TimedValue(Instant.ofEpochSecond(2), 2), new TimedValue(Instant.ofEpochSecond(1), 1));
		assertThat(SeriesDownsampler.downsample(MetricType.CPU_MACHINE, small, 10)).extracting(MetricSample::getAvgValue)
				.containsExactly(1.0, 2.0);
		List<TimedValue> large = new ArrayList<>();
		IntStream.range(0, 100).forEach(i -> large.add(new TimedValue(Instant.ofEpochSecond(i), i)));
		List<MetricSample> reduced = SeriesDownsampler.downsample(MetricType.HEAP_USED, large, 10);
		assertThat(reduced).hasSize(10);
		assertThat(reduced.get(0).getAvgValue()).isEqualTo(4.5);
		assertThat(reduced.get(0).getMaxValue()).isEqualTo(9.0);
		assertThat(reduced.get(9).getMaxValue()).isEqualTo(99.0);
	}

	@Test
	void stats() {
		assertThat(Stats.avg(List.of())).isNull();
		assertThat(Stats.max(List.of())).isNull();
		assertThat(Stats.avg(List.of(1.0, 3.0))).isEqualTo(2.0);
		assertThat(Stats.percentile(List.of(), 0.95)).isZero();
		List<Double> values = IntStream.rangeClosed(1, 100).mapToObj(i -> (double) i).toList();
		assertThat(Stats.percentile(values, 0.95)).isEqualTo(95.0);
		assertThat(Stats.percentile(List.of(7.0), 0.95)).isEqualTo(7.0);
	}

	@Test
	void jvmArgsParser() {
		String args = "-Xms512m -Xmx2g -XX:TieredStopAtLevel=4 -XX:TieredStopAtLevel=1 -XX:ActiveProcessorCount=2";
		assertThat(JvmArgsParser.xxValue(args, "TieredStopAtLevel")).contains("1");
		assertThat(JvmArgsParser.xxValue(null, "TieredStopAtLevel")).isEmpty();
		assertThat(JvmArgsParser.xxCount(args, "TieredStopAtLevel")).isEqualTo(2);
		assertThat(JvmArgsParser.xxCount(null, "X")).isZero();
		assertThat(JvmArgsParser.memoryFlag(args, "-Xms")).contains(512L << 20);
		assertThat(JvmArgsParser.memoryFlag(args, "-Xmx")).contains(2L << 30);
		assertThat(JvmArgsParser.memoryFlag("-Xmx100", "-Xmx")).contains(100L);
		assertThat(JvmArgsParser.memoryFlag("-Xmx1t -Xss8k", "-Xss")).contains(8L << 10);
		assertThat(JvmArgsParser.memoryFlag("-Xmx1t", "-Xmx")).contains(1L << 40);
		assertThatThrownBy(() -> JvmArgsParser.toBytes("abc")).isInstanceOf(IllegalArgumentException.class);
		assertThat(JvmArgsParser.jarVersion("/opt/app/billing-service-1.4.2.jar --port 8080")).contains("1.4.2");
		assertThat(JvmArgsParser.jarVersion("app-2.0.0-SNAPSHOT.jar")).contains("2.0.0-SNAPSHOT");
		assertThat(JvmArgsParser.jarVersion("com.example.Main")).isEmpty();
		assertThat(JvmArgsParser.jarVersion(null)).isEmpty();
	}

	@Test
	void stackFramesAndCounter() {
		assertThat(StackFrames.top(null)).isEmpty();
		assertThat(StackFrames.topMethod(null)).isNull();
		IMCStackTrace empty = Mockito.mock(IMCStackTrace.class);
		Mockito.doReturn(List.of()).when(empty).getFrames();
		assertThat(StackFrames.topMethod(empty)).isNull();
		IMCFrame noMethod = Mockito.mock(IMCFrame.class);
		Mockito.when(noMethod.getFrameLineNumber()).thenReturn(7);
		IMCStackTrace unknown = Mockito.mock(IMCStackTrace.class);
		Mockito.doReturn(List.of(noMethod)).when(unknown).getFrames();
		assertThat(StackFrames.top(unknown)).containsExactly(new StackFrame("(desconhecido)", "(desconhecido)", null, 7));
		assertThat(StackFrames.topMethod(unknown)).isEqualTo("(desconhecido)");

		FrameCounter counter = new FrameCounter();
		assertThat(counter.mostFrequent()).isEmpty();
		List<StackFrame> a = List.of(new StackFrame("A", "a", null, 1));
		List<StackFrame> b = List.of(new StackFrame("B", "b", null, 1));
		counter.add(a);
		counter.add(b);
		counter.add(b);
		counter.add(List.of());
		assertThat(counter.mostFrequent()).isEqualTo(b);
		assertThat(new StackFrame("A", "a", null, null).display()).isEqualTo("A.a");
		assertThat(new StackFrame("A", "a", null, 3).display()).isEqualTo("A.a:3");
	}

	@Test
	void smallHelpers() {
		RecordingSettings settings = new RecordingSettings(Map.of("jdk.A", Map.of("enabled", "true")));
		assertThat(settings.enabled("jdk.A")).contains(true);
		assertThat(settings.enabled("jdk.B")).isEmpty();
	}
}
