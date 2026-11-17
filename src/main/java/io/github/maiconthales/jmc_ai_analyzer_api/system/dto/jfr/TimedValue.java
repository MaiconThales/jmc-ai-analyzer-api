package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import java.time.Instant;

/** Valor numérico associado a um instante. */
public record TimedValue(Instant ts, double value) {
}
