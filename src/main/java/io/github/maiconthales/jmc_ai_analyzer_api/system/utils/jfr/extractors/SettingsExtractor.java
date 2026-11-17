package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.ExtractionContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.RecordingSettings;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEventTypes;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JfrEvents;
import java.util.HashMap;
import java.util.Map;

/** Configuração ativa da gravação ({@code jdk.ActiveSetting}); a última ocorrência prevalece. */
public final class SettingsExtractor {

	private SettingsExtractor() {
	}

	public static RecordingSettings extract(ExtractionContext ctx) {
		Map<String, Map<String, String>> settings = new HashMap<>();
		JfrEvents.forEach(ctx.items(), JfrEventTypes.ACTIVE_SETTING, e -> {
			String eventType = e.identifier("settingFor");
			String name = e.text("name");
			String value = e.text("value");
			if (eventType != null && name != null && value != null) {
				settings.computeIfAbsent(eventType, k -> new HashMap<>()).put(name, value);
			}
		});
		return new RecordingSettings(settings);
	}
}
