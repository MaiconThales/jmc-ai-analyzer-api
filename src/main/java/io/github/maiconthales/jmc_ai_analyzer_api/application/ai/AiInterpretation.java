package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

/** Provider-neutral, versioned result contract. Provider execution is out of scope here. */
public record AiInterpretation(
        String status,
        String provider,
        String model,
        String promptVersion,
        String payload,
        String summary) {
    public AiInterpretation {
        status = Text.required(status, "status");
        provider = Text.required(provider, "provider");
        model = Text.required(model, "model");
        promptVersion = Text.required(promptVersion, "promptVersion");
        payload = Text.providerPayload(payload, "payload");
        summary = Text.bounded(summary, "summary");
    }
}
