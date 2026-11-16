package io.github.maiconthales.jmc_ai_analyzer_api.application.ai;

/** Provider-neutral seam. A future provider receives the bounded contract only. */
public interface AiInterpretationService {
    AiInterpretation interpret(AnalysisResult result);
}
