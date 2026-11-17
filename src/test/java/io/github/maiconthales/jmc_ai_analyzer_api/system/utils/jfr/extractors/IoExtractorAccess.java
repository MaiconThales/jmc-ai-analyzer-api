package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.EventView;

/** Expõe os helpers de alvo de I/O para testes em outros pacotes. */
public final class IoExtractorAccess {

	private IoExtractorAccess() {
	}

	public static String socket(EventView view) {
		return IoExtractor.socketTarget(view);
	}

	public static String file(EventView view) {
		return IoExtractor.fileTarget(view);
	}
}
