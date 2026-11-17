package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingMetadata;
import org.junit.jupiter.api.Test;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.ItemCollectionToolkit;

/** Gravação sem eventos: todos os domínios ficam indisponíveis, sem erro. */
class EmptyRecordingTest {

	private static final IItemCollection EMPTY = ItemCollectionToolkit.EMPTY;

	@Test
	void everyExtractorHandlesMissingEvents() {
		RecordingMetadata metadata = MetadataExtractor.extract(EMPTY);
		assertThat(metadata.start()).isNull();
		assertThat(metadata.durationMs()).isZero();
		assertThat(metadata.jvmName()).isNull();
		assertThat(metadata.cpuCores()).isNull();
		assertThat(metadata.physicalMemoryBytes()).isNull();

		ExtractionContext ctx = new ExtractionContext(EMPTY, metadata, 10, 300);
		assertThat(CpuExtractor.extract(ctx).available()).isFalse();
		assertThat(HeapExtractor.extract(ctx).available()).isFalse();
		assertThat(HeapExtractor.extract(ctx).youngCollector()).isNull();
		assertThat(GcExtractor.extract(ctx).available()).isFalse();
		assertThat(ThreadExtractor.extract(ctx).peak()).isNull();
		assertThat(LockExtractor.extract(ctx).available()).isFalse();
		assertThat(HotMethodExtractor.extract(ctx).available()).isFalse();
		assertThat(AllocationExtractor.extract(ctx).rateBytesPerSecond()).isNull();
		assertThat(ExceptionExtractor.extract(ctx).available()).isFalse();
		assertThat(IoExtractor.extract(ctx).totals()).isEmpty();
		assertThat(LeakExtractor.extract(ctx).available()).isFalse();
		assertThat(ThreadCpuExtractor.extract(ctx).available()).isFalse();
		assertThat(SettingsExtractor.extract(ctx).byEventType()).isEmpty();
	}
}
