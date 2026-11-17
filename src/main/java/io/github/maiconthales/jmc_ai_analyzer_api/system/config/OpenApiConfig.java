package io.github.maiconthales.jmc_ai_analyzer_api.system.config;

import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados da documentação OpenAPI / Swagger UI. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI openApi(AnalysisProperties properties) {
		return new OpenAPI().info(new Info()
				.title("JMC AI Analyzer API")
				.version(properties.parserVersion())
				.description("Recebe gravações JFR, extrai métricas e gera um diagnóstico priorizado para dashboard. "
						+ "Erros seguem application/problem+json (RFC 9457) com a propriedade 'code'."));
	}
}
