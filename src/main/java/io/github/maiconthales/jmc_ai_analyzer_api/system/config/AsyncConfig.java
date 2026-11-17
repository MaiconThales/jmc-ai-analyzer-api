package io.github.maiconthales.jmc_ai_analyzer_api.system.config;

import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Executor limitado das análises e agendador do watchdog de timeout. */
@Configuration(proxyBeanMethods = false)
public class AsyncConfig {

	public static final String ANALYSIS_EXECUTOR = "analysisExecutor";

	@Bean(name = ANALYSIS_EXECUTOR)
	ThreadPoolTaskExecutor analysisExecutor(AnalysisProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.maxConcurrent());
		executor.setMaxPoolSize(properties.maxConcurrent());
		executor.setThreadNamePrefix("analysis-");
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}

	@Bean(destroyMethod = "shutdownNow")
	ScheduledExecutorService analysisWatchdog() {
		return Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(r, "analysis-watchdog");
			thread.setDaemon(true);
			return thread;
		});
	}
}
