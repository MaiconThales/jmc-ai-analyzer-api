package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import org.openjdk.jmc.common.item.IItemCollection;

/**
 * Entrada comum dos extratores.
 *
 * @param items     eventos da gravação
 * @param metadata  metadados já extraídos (duração, núcleos)
 * @param topN      quantidade de itens de top-N a manter
 * @param maxPoints pontos máximos por série
 */
public record ExtractionContext(IItemCollection items, RecordingMetadata metadata, int topN, int maxPoints) {
}
