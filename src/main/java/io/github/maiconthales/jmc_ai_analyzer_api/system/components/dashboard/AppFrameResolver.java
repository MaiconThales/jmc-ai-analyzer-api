package io.github.maiconthales.jmc_ai_analyzer_api.system.components.dashboard;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.AppFrames;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolve, na consulta, o primeiro frame do código da aplicação (RF-15). */
@Component
@RequiredArgsConstructor
public class AppFrameResolver {

	private final DiagnosisProperties properties;

	public StackFrame resolve(List<StackFrame> frames, List<String> appPackages) {
		return AppFrames.resolve(frames, appPackages, properties.frameworkPackages());
	}
}
