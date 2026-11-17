package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class EntityTest {

	@Test
	void rootEntitiesUseIdentifierEquality() {
		check(() -> new Software("a", 1), Software.class);
		check(() -> new AnalysisBatch(UUID.randomUUID(), null, null, null, 1), AnalysisBatch.class);
		check(() -> new JfrRecording(UUID.randomUUID(), "a", "b", "k", "s", 1), JfrRecording.class);
		Software noId = new Software("a", 1);
		noId.setId(null);
		Software other = new Software("b", 1);
		other.setId(null);
		assertThat(noId).isNotEqualTo(other);
		AnalysisBatch batchNoId = new AnalysisBatch(UUID.randomUUID(), null, null, null, 1);
		batchNoId.setId(null);
		assertThat(batchNoId).isNotEqualTo(new AnalysisBatch(UUID.randomUUID(), null, null, null, 1));
		JfrRecording recordingNoId = new JfrRecording(UUID.randomUUID(), "a", "b", "k", "s", 1);
		recordingNoId.setId(null);
		assertThat(recordingNoId).isNotEqualTo(new JfrRecording(UUID.randomUUID(), "a", "b", "k", "s", 1));
		RecordingSummary a = new RecordingSummary();
		RecordingSummary b = new RecordingSummary();
		assertThat(a).isNotEqualTo(b).isEqualTo(a).isNotEqualTo("x");
		a.setRecordingId(UUID.randomUUID());
		b.setRecordingId(a.getRecordingId());
		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(new RecordingSummary());
	}

	@Test
	void childEntitiesUseIdentifierEquality() {
		HotMethod a = new HotMethod();
		HotMethod b = new HotMethod();
		assertThat(a).isEqualTo(a).isNotEqualTo(b).isNotEqualTo(null).isNotEqualTo(new GcPause());
		a.setId(1L);
		assertThat(a).isNotEqualTo(b);
		b.setId(1L);
		assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
		b.setId(2L);
		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void softwarePackages() {
		Software software = new Software("app", 10);
		assertThat(software.appPackageList()).isEmpty();
		software.setAppPackages("  ");
		assertThat(software.appPackageList()).isEmpty();
		software.replaceAppPackages(List.of("com.a", "com.b"));
		assertThat(software.getAppPackages()).isEqualTo("com.a,com.b");
		software.setAppPackages("com.a, ,com.b ");
		assertThat(software.appPackageList()).containsExactly("com.a", "com.b");
		software.replaceAppPackages(List.of());
		assertThat(software.getAppPackages()).isNull();
		software.replaceAppPackages(null);
		assertThat(software.getAppPackages()).isNull();
	}

	private static <T> void check(Supplier<T> factory, Class<T> type) {
		T a = factory.get();
		T b = factory.get();
		assertThat(a).isEqualTo(a).isNotEqualTo(b).isNotEqualTo(null).isNotEqualTo("x");
		assertThat(a.hashCode()).isEqualTo(type.hashCode());
	}
}
