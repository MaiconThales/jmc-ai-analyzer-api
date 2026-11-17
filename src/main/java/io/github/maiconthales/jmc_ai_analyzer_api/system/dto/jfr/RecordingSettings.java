package io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr;

import java.util.Map;
import java.util.Optional;

/**
 * Configuração usada na gravação ({@code jdk.ActiveSetting}): tipo de evento → (nome → valor).
 */
public record RecordingSettings(Map<String, Map<String, String>> byEventType) {

	public Optional<String> value(String eventType, String setting) {
		return Optional.ofNullable(byEventType.get(eventType)).map(s -> s.get(setting));
	}

	/**
	 * Indica se o evento estava habilitado.
	 *
	 * @return vazio quando a gravação não informa a configuração do evento
	 */
	public Optional<Boolean> enabled(String eventType) {
		return value(eventType, "enabled").map(Boolean::parseBoolean);
	}
}
