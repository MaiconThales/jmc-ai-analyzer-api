package io.github.maiconthales.jmc_ai_analyzer_api.application;

import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisSession;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisStage;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AnalysisResult;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.AiInterpretation;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.persistence.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisQueryService {
    private final AnalysisSessionRepository sessions;
    private final AnalysisStageRepository stages;
    private final AnalysisResultRepository results;
    private final AiInterpretationRepository interpretations;

    public AnalysisQueryService(AnalysisSessionRepository sessions, AnalysisStageRepository stages,
                                 AnalysisResultRepository results, AiInterpretationRepository interpretations) {
        this.sessions = sessions; this.stages = stages; this.results = results; this.interpretations = interpretations;
    }

    @Transactional(readOnly = true)
    public Snapshot get(UUID id) {
        AnalysisSession session = sessions.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
        return new Snapshot(session, stages.findAllByAnalysisIdOrderByIdAsc(id),
                results.findAllByAnalysisIdOrderByIdAsc(id), interpretations.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(id));
    }

    @Transactional(readOnly = true)
    public Page<Snapshot> history(Pageable pageable) {
        return sessions.findHistory(pageable).map(session -> new Snapshot(session,
                stages.findAllByAnalysisIdOrderByIdAsc(session.getId()),
                results.findAllByAnalysisIdOrderByIdAsc(session.getId()),
                interpretations.findAllByAnalysis_IdOrderByCreatedAtDescIdDesc(session.getId())));
    }

    public record Snapshot(AnalysisSession session, List<AnalysisStage> stages,
                           List<AnalysisResult> results, List<AiInterpretation> interpretations) { }
    public static class AnalysisNotFoundException extends RuntimeException {
        public AnalysisNotFoundException(UUID id) { super("Analysis not found: " + id); }
    }
}
