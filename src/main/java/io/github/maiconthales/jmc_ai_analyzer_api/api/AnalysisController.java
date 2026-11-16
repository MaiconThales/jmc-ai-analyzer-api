package io.github.maiconthales.jmc_ai_analyzer_api.api;

import io.github.maiconthales.jmc_ai_analyzer_api.application.AnalysisApplicationService;
import io.github.maiconthales.jmc_ai_analyzer_api.application.AnalysisQueryService;
import io.github.maiconthales.jmc_ai_analyzer_api.domain.model.*;
import io.github.maiconthales.jmc_ai_analyzer_api.infrastructure.filesystem.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/v1/analyses", produces = MediaType.APPLICATION_JSON_VALUE)
public class AnalysisController {
    private final AnalysisApplicationService application;
    private final AnalysisQueryService query;
    private final HttpJsonPayloadNormalizer payloadNormalizer = new HttpJsonPayloadNormalizer();

    public AnalysisController(AnalysisApplicationService application, AnalysisQueryService query) {
        this.application = application; this.query = query;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisResponse> create(@Valid @RequestBody AnalysisRequest request) {
        var session = application.createAndSchedule(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AnalysisResponse(session.getId(), session.getStatus()));
    }

    @GetMapping("/{id}")
    public AnalysisHttpDtos.Analysis get(@PathVariable String id) { return detail(uuid(id)); }

    @GetMapping("/{id}/status")
    public AnalysisHttpDtos.Status status(@PathVariable String id) {
        var s = query.get(uuid(id));
        return new AnalysisHttpDtos.Status(s.session().getId(), s.session().getStatus(), s.session().getErrorCount(), stages(s.stages()));
    }

    @GetMapping("/{id}/interpretation")
    public AnalysisHttpDtos.Interpretation interpretation(@PathVariable String id) {
        var s = query.get(uuid(id));
        if (s.interpretations().isEmpty()) throw new AnalysisQueryService.AnalysisNotFoundException(uuid(id));
        return interpretation(s.interpretations().get(0));
    }

    @GetMapping("/history")
    public AnalysisHttpDtos.History history(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100) throw new IllegalArgumentException("page/size out of bounds");
        var result = query.history(PageRequest.of(page, size));
        return new AnalysisHttpDtos.History(result.getContent().stream().map(this::map).toList(), page, size,
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    private AnalysisHttpDtos.Analysis detail(UUID id) { return map(query.get(id)); }
    private AnalysisHttpDtos.Analysis map(AnalysisQueryService.Snapshot s) {
        var errors = s.stages().stream().filter(x -> x.getErrorCode() != null)
                .map(x -> new AnalysisHttpDtos.Error(payloadNormalizer.normalizeText(x.getErrorCode()),
                        payloadNormalizer.normalizeText(x.getErrorMessage()))).toList();
        return new AnalysisHttpDtos.Analysis(session(s.session()), stages(s.stages()),
                s.results().stream().map(x -> new AnalysisHttpDtos.Result(x.getId(), x.getResultType(), payloadNormalizer.normalize(x.getPayload()), x.getCreatedAt())).toList(),
                errors, s.interpretations().isEmpty() ? null : interpretation(s.interpretations().get(0)));
    }
    private AnalysisHttpDtos.Session session(AnalysisSession s) { return new AnalysisHttpDtos.Session(s.getId(), s.getFileId(), s.getFileName(), s.getFileSizeBytes(), s.getFileModifiedAt(), s.getStatus(), s.getStartedAt(), s.getFinishedAt(), s.getCreatedAt(), s.getErrorCount()); }
    private List<AnalysisHttpDtos.Stage> stages(List<AnalysisStage> values) { return values.stream().map(x -> new AnalysisHttpDtos.Stage(x.getId(), x.getStageName(), x.getStatus(), x.getStartedAt(), x.getFinishedAt(), x.getErrorCode() == null ? null : new AnalysisHttpDtos.Error(payloadNormalizer.normalizeText(x.getErrorCode()), payloadNormalizer.normalizeText(x.getErrorMessage())))).toList(); }
    private AnalysisHttpDtos.Interpretation interpretation(AiInterpretation x) { return new AnalysisHttpDtos.Interpretation(x.getId(), x.getStatus().name(), payloadNormalizer.normalizeText(x.getProvider()), payloadNormalizer.normalizeText(x.getModel()), payloadNormalizer.normalizeText(x.getPromptVersion()), payloadNormalizer.normalizeText(x.getExecutiveSummary()), payloadNormalizer.normalizeText(x.getOverallSeverity()), payloadNormalizer.normalize(x.getPayload()), x.getErrorCode() == null ? null : new AnalysisHttpDtos.Error(payloadNormalizer.normalizeText(x.getErrorCode()), payloadNormalizer.normalizeText(x.getErrorMessage())), x.getCreatedAt()); }
    private static UUID uuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new InvalidUuidException(); } }
}
