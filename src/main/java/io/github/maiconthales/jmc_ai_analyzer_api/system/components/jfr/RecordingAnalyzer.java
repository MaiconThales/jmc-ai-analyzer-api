package io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingExtraction;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingMetadata;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.AnalysisInterruptedException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.JfrParseException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.AllocationExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.CpuExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.ExceptionExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.GcExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.HeapExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.HotMethodExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.IoExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.LeakExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.LockExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.MetadataExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.SettingsExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.ThreadCpuExtractor;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.ThreadExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.springframework.stereotype.Component;

/**
 * Carrega uma gravação e executa todos os extratores e as regras do JMC.
 * Verifica a interrupção da thread entre as etapas, para respeitar o timeout.
 */
@Component
@RequiredArgsConstructor
public class RecordingAnalyzer {

	private final AnalysisProperties properties;
	private final JmcRuleRunner ruleRunner;

	public RecordingExtraction analyze(Path file) {
		IItemCollection items = load(file);
		RecordingMetadata metadata = step(() -> MetadataExtractor.extract(items));
		ExtractionContext ctx = new ExtractionContext(items, metadata, properties.persistedTopN(),
				properties.maxSeriesPoints());
		return new RecordingExtraction(metadata,
				step(() -> CpuExtractor.extract(ctx)),
				step(() -> HeapExtractor.extract(ctx)),
				step(() -> GcExtractor.extract(ctx)),
				step(() -> ThreadExtractor.extract(ctx)),
				step(() -> LockExtractor.extract(ctx)),
				step(() -> HotMethodExtractor.extract(ctx)),
				step(() -> AllocationExtractor.extract(ctx)),
				step(() -> ExceptionExtractor.extract(ctx)),
				step(() -> IoExtractor.extract(ctx)),
				step(() -> LeakExtractor.extract(ctx)),
				step(() -> ThreadCpuExtractor.extract(ctx)),
				step(() -> ruleRunner.run(items)),
				step(() -> SettingsExtractor.extract(ctx)),
				step(() -> JfrEvents.countByType(items)));
	}

	static IItemCollection load(Path file) {
		try {
			return JfrLoaderToolkit.loadEvents(file.toFile());
		} catch (IOException | CouldNotLoadRecordingException | RuntimeException e) {
			throw new JfrParseException("Não foi possível ler a gravação JFR: " + e.getMessage(), e);
		}
	}

	private static <T> T step(Supplier<T> extractor) {
		if (Thread.currentThread().isInterrupted()) {
			throw new AnalysisInterruptedException();
		}
		return extractor.get();
	}

}
