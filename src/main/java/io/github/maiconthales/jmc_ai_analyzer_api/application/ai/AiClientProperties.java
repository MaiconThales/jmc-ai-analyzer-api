package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration for the provider-neutral HTTP adapter. Secrets are supplied by the environment. */
@ConfigurationProperties(prefix = "ai")
public class AiClientProperties {
    private boolean enabled = false;
    private String provider = "provider-neutral";
    private String baseUrl = "http://localhost:11434/v1/chat/completions";
    private String model = "default";
    private Duration timeout = Duration.ofSeconds(30);
    private int retryMaxAttempts = 1;
    private String promptVersion = PromptBuilder.DEFAULT_VERSION;
    private String apiKey;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
