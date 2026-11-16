package io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.mcp;

import io.github.maiconthales.jmc_ai_analyzer_api.application.McpAnalysisExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

@Configuration
public class AnalysisExecutorConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "analysis")
    public AnalysisExecutorProperties analysisExecutorProperties() {
        return new AnalysisExecutorProperties();
    }

    @Bean(name = "analysisExecutor", destroyMethod = "shutdown")
    public ExecutorService analysisExecutor(AnalysisExecutorProperties properties) {
        return new ThreadPoolExecutor(
                properties.getMaxConcurrent(),
                properties.getMaxConcurrent(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(5),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public McpAnalysisExecutor mcpAnalysisExecutor(
            @Qualifier("analysisExecutor") ExecutorService executor,
            AnalysisExecutorProperties properties) {
        return new McpAnalysisExecutor(executor, properties.getStageTimeout(), properties.getSessionTimeout());
    }

    public static class AnalysisExecutorProperties {
        private int maxConcurrent = 2;
        private Duration stageTimeout = Duration.ofSeconds(30);
        private Duration sessionTimeout = Duration.ofMinutes(2);

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            if (maxConcurrent < 1) {
                throw new IllegalArgumentException("analysis.max-concurrent must be positive");
            }
            this.maxConcurrent = maxConcurrent;
        }

        public Duration getStageTimeout() {
            return stageTimeout;
        }

        public void setStageTimeout(Duration stageTimeout) {
            this.stageTimeout = requirePositive(stageTimeout, "analysis.stage-timeout");
        }

        public Duration getSessionTimeout() {
            return sessionTimeout;
        }

        public void setSessionTimeout(Duration sessionTimeout) {
            this.sessionTimeout = requirePositive(sessionTimeout, "analysis.session-timeout");
        }

        private static Duration requirePositive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}
