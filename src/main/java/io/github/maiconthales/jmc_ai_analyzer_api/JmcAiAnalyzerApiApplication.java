package io.github.maiconthales.jmc_ai_analyzer_api;

import java.util.Locale;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class JmcAiAnalyzerApiApplication {

	public static void main(String[] args) {
		// As mensagens do JMC formatam números e datas com o locale padrão: fixar garante resultados determinísticos.
		Locale.setDefault(Locale.US);
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(JmcAiAnalyzerApiApplication.class, args);
	}

}
