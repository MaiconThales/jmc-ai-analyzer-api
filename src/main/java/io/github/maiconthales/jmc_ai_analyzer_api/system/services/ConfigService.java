package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.UploadValidator;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.ConfigResponse;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.RetentionProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Expõe os limites configurados, para que o front use a mesma fonte de configuração da API. */
@Service
@RequiredArgsConstructor
public class ConfigService {

	private final UploadProperties upload;
	private final AnalysisProperties analysis;
	private final RetentionProperties retention;

	public ConfigResponse current() {
		return new ConfigResponse(
				new ConfigResponse.Upload(upload.maxFiles(), upload.maxFileSize().toBytes(),
						upload.maxRequestSize().toBytes(), UploadValidator.EXTENSION),
				new ConfigResponse.Dashboard(analysis.defaultTopN(), analysis.maxTopN()),
				new ConfigResponse.Retention(retention.defaultDays()));
	}
}
