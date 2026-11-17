package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import java.util.List;

/**
 * Escolhe o primeiro frame do código da aplicação numa pilha.
 * Com {@code appPackages}, é o primeiro frame desses pacotes; sem eles, o primeiro fora dos pacotes de framework.
 */
public final class AppFrames {

	private AppFrames() {
	}

	public static StackFrame resolve(List<StackFrame> frames, List<String> appPackages, List<String> frameworkPackages) {
		if (frames == null || frames.isEmpty()) {
			return null;
		}
		for (StackFrame frame : frames) {
			String type = frame.c();
			if (type == null) {
				continue;
			}
			boolean app = appPackages.isEmpty()
					? frameworkPackages.stream().noneMatch(type::startsWith)
					: appPackages.stream().anyMatch(type::startsWith);
			if (app) {
				return frame;
			}
		}
		return null;
	}
}
