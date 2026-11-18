package io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.*;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.AnalysisInterruptedException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.exceptions.JfrParseException;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Carrega uma gravação e executa todos os extratores e as regras do JMC.
 * Verifica a interrupção da thread entre as etapas, para respeitar o timeout.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class RecordingAnalyzer {

    private final AnalysisProperties properties;
    private final JmcRuleRunner ruleRunner;

    public RecordingExtraction analyze(Path file) {
        IItemCollection items = load(file);
        RecordingMetadata metadata = step(() -> MetadataExtractor.extract(items), RecordingMetadata.empty());
        ExtractionContext ctx = new ExtractionContext(items, metadata, properties.persistedTopN(),
                properties.maxSeriesPoints());
        return new RecordingExtraction(metadata,
                step(() -> CpuExtractor.extract(ctx), DomainResults.Cpu.empty()),
                step(() -> HeapExtractor.extract(ctx), DomainResults.Heap.empty()),
                step(() -> GcExtractor.extract(ctx), DomainResults.Gc.empty()),
                step(() -> ThreadExtractor.extract(ctx), DomainResults.Threads.empty()),
                step(() -> LockExtractor.extract(ctx), DomainResults.Locks.empty()),
                step(() -> HotMethodExtractor.extract(ctx), DomainResults.HotMethods.empty()),
                step(() -> AllocationExtractor.extract(ctx), DomainResults.Allocation.empty()),
                step(() -> ExceptionExtractor.extract(ctx), DomainResults.Exceptions.empty()),
                step(() -> IoExtractor.extract(ctx), DomainResults.Io.empty()),
                step(() -> LeakExtractor.extract(ctx), DomainResults.Leaks.empty()),
                step(() -> ThreadCpuExtractor.extract(ctx), DomainResults.ThreadCpuLoad.empty()),
                step(() -> ruleRunner.run(items), JmcRulesResult.empty()),
                step(() -> SettingsExtractor.extract(ctx), RecordingSettings.empty()),
                step(() -> JfrEvents.countByType(items), Map.of()));
    }

    static IItemCollection load(Path file) {
        try {
            return JfrLoaderToolkit.loadEvents(file.toFile());
        } catch (IOException | CouldNotLoadRecordingException | RuntimeException e) {
            throw new JfrParseException("Não foi possível ler a gravação JFR: " + e.getMessage(), e);
        }
    }

    private static <T> T step(Supplier<T> extractor, T fallback) {
        if (Thread.currentThread().isInterrupted()) {
            throw new AnalysisInterruptedException();
        }
        try {
            T result = extractor.get();
            return result != null ? result : fallback;
        } catch (AnalysisInterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao executar extrator (evento ausente ou incompleto). Retornando empty(): {}", e.getMessage());
            return fallback;
        }
    }

}
