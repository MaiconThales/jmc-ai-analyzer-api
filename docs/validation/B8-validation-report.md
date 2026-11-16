# B8 validation report

This report is deliberately deterministic: it records commands and repository evidence, not a local timestamp.

## Evidence matrix

| Task | Evidence | Status |
|---|---|---|
| B8.1 | `McpAnalysisOrchestratorTest` drives the real allow-listed adapter with a simulated `McpProtocolClient`. | PASS |
| B8.2 | `McpAnalysisOrchestratorTest#invokesEveryAllowListedToolWithControlledFilePath`; `McpAnalysisExecutorTest`. | PASS |
| B8.3 | `McpAnalysisOrchestratorTest#preservesSuccessfulStagesWhenTheSimulatedMcpClientFailsOneTool`. | PASS |
| B8.4 | `McpAnalysisExecutorTest` covers all-stage failure and timeout; `McpAnalysisOrchestratorTest#turnsAClientThatNeverCompletesIntoStageTimeouts`. | PASS |
| B8.5 | `McpAnalysisAiCoordinatorTest` and `HttpAiInterpretationServiceTest` use simulated provider seams. | PASS |
| B8.6 | `HttpAiInterpretationServiceTest` covers invalid response, timeout, 4xx/5xx and connection failure. | PASS |
| B8.7 | `AnalysisControllerTest`, `JfrFileControllerTest` and `ApiExceptionHandlerTest` use MockMvc. | PASS |
| B8.8 | `Dockerfile.test` and `scripts/validate-tests-container.ps1` were executed; Maven tests reached the Testcontainers suite, then stopped because `/var/run/docker.sock` is unavailable inside the build container. | PENDING |

## Commands

```powershell
.\mvnw.cmd -B test
.\scripts\validate-compose.ps1
.\scripts\validate-tests-container.ps1
```

The Compose validation intentionally remains dependent on externally provisioned `FRONTEND_IMAGE`/`FRONTEND_USER`; MCP and AI profiles additionally require their external artifacts. This does not change the pending status of B7.1, B7.4 or B7.7.
