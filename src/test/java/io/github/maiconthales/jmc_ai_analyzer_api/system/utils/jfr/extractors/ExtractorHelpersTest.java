package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractorHelpersTest {

	@Test
	void helpersHandleNullAndLongValues() {
		assertThat(MetadataExtractor.osName(null)).isNull();
		assertThat(MetadataExtractor.osName("x".repeat(600))).hasSize(500);
		assertThat(MetadataExtractor.osName("OS: Linux\nuptime")).isEqualTo("OS: Linux");
		assertThat(ExceptionExtractor.truncate(null)).isNull();
		assertThat(ExceptionExtractor.truncate("y".repeat(600))).hasSize(500);
		assertThat(ExceptionExtractor.throwablesTotal(List.of())).isNull();
	}
}
