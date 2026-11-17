package io.github.maiconthales.jmc_ai_analyzer_api.config;

import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.RetentionProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.UploadProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.util.unit.DataSize;

/** Propriedades com os valores padrão, para testes sem Spring. */
public final class TestProperties {

	private TestProperties() {
	}

	public static AnalysisProperties analysis() {
		return new AnalysisProperties(2, Duration.ofMinutes(10), 300, 50, 10, 50, 2, "test+jmc");
	}

	public static UploadProperties upload() {
		return new UploadProperties(20, DataSize.ofMegabytes(128), DataSize.ofGigabytes(1));
	}

	public static RetentionProperties retention() {
		return new RetentionProperties(90, "0 0 3 * * *");
	}

	public static DiagnosisProperties diagnosis() {
		return new DiagnosisProperties(new DiagnosisProperties.PriorityScore(80, 50, 15),
				new DiagnosisProperties.HealthWeights(20, 8, 2),
				List.of("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.springframework.", "org.apache.", "io.netty."),
				85, 200, 5, DataSize.ofGigabytes(1), 90, 10, 20, 1, 100, 1000, 2, 0.5,
				new DiagnosisProperties.Coverage(1000, Duration.ofMinutes(5)));
	}
}
