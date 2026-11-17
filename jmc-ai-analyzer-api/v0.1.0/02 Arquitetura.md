---
tags: [jmc-ai-analyzer, v0-1-0, arquitetura]
versao: 0.1.0
status: rascunho
---

# Arquitetura

Voltar: [[00 Índice]]

## Stack

| Item | Escolha | Observação |
|---|---|---|
| Linguagem | Java 17 | |
| Framework | Spring Boot **4.1.2** | Spring Framework 7, Hibernate 7.4, **Jackson 3** (`tools.jackson.*`) |
| Banco | PostgreSQL | driver 42.7.x, gerenciado pelo Boot |
| Migrações | Flyway 12 | precisa de `flyway-database-postgresql` |
| Leitura JFR | `org.openjdk.jmc:flightrecorder` + `flightrecorder.rules.jdk` **9.1.2** | bytecode Java 17, conferido no Maven Central |
| Boilerplate | Lombok 1.18.48 | gerenciado pelo Boot |
| Blob storage | SeaweedFS (API S3) + AWS SDK for Java v2 | ver [[09 Armazenamento blob]] |
| Container | Docker + Compose, com dados mapeados no host | ver [[07 Infraestrutura e qualidade#Persistência no host]] |
| Testes | JUnit 5, Mockito, Testcontainers 2.0.5 | ver [[07 Infraestrutura e qualidade#Testes]] |

### Starters do Spring Boot 4.1.2

> [!warning] O Boot 4 reorganizou os starters
> Os nomes abaixo foram conferidos no Maven Central para a versão 4.1.2. Não use os nomes do Boot 3 de memória.

| Uso | Artefato |
|---|---|
| REST | `spring-boot-starter-webmvc` |
| JPA | `spring-boot-starter-data-jpa` |
| Validação | `spring-boot-starter-validation` |
| Flyway | `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` |
| Health | `spring-boot-starter-actuator` |
| Swagger (fora do Boot) | `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.x |
| Testes web | `spring-boot-starter-webmvc-test` |
| Testes JPA | `spring-boot-starter-data-jpa-test` |
| Testes Flyway | `spring-boot-starter-flyway-test` |
| Testcontainers | `spring-boot-testcontainers` + `org.testcontainers:testcontainers-postgresql` (+ `GenericContainer` do SeaweedFS) |
| S3 (fora do Boot) | `software.amazon.awssdk:s3`, versão pelo `software.amazon.awssdk:bom` |

## Estrutura de pacotes

Definida em [[Arquivo bruto]]. Os subpacotes só existem quando forem necessários.

```text
io.github.maiconthales.jmc_ai_analyzer_api
├── JmcAiAnalyzerApiApplication.java      (@SpringBootApplication, @EnableScheduling, @ConfigurationPropertiesScan)
├── system
│   ├── config         @Configuration: AsyncConfig, BlobStorageConfig, ClockConfig, OpenApiConfig, WebConfig
│   ├── properties     @ConfigurationProperties: UploadProperties, AnalysisProperties, BlobProperties,
│   │                  StorageProperties, RetentionProperties, DiagnosisProperties
│   ├── controllers    AnalysisController, RecordingController, SoftwareController
│   ├── dto            DTOs HTTP: BatchResponse, RecordingStatusResponse, SoftwareResponse, SoftwareUpdateRequest,
│   │   │              UploadRequest, HistoryItemResponse, DashboardDtos
│   │   ├── jfr        RecordingExtraction, RecordingMetadata, DomainResults, RecordingSettings, ExtractionContext,
│   │   │              TimedValue, JmcRulesResult, NotApplicableRule
│   │   ├── diagnosis  DiagnosisContext, DiagnosisOutcome, CoverageResult, CoverageDomainSpec, JmcTranslation
│   │   ├── dashboard  RecordingDataSet, ConsolidationInput
│   │   └── processing ProcessingTarget, StoredFile, RecordingsUploadedEvent, BlobObject, RetentionResult
│   ├── exceptions     ApiException (base, com ErrorCode), NotFoundException, NotReadyException,
│   │                  InvalidUploadException, StorageUnavailableException, BlobNotFoundException,
│   │                  JfrParseException, AnalysisInterruptedException
│   ├── handlers       ApiExceptionHandler (@RestControllerAdvice → ProblemDetail)
│   ├── components     @Component: UploadValidator, AnalysisDispatcher, RecoveryRunner, RetentionJob,
│   │   │              BlobStorageHealthIndicator
│   │   ├── jfr        RecordingAnalyzer, JmcRuleRunner
│   │   ├── dashboard  RecordingDataLoader, AppFrameResolver
│   │   └── diagnosis  JmcFindingTranslator, DiagnosisRule (interface)
│   │       └── rules  HeapPressureRule, GcPauseHighRule, AllocationRateHighRule, CpuSaturationRule,
│   │                  HotMethodDominantRule, LockContentionRule, ExceptionRateHighRule, SlowRemoteCallRule,
│   │                  ThreadGrowthRule, TieredStopLevelRule, ActiveProcessorCountRule, XmsXmxMismatchRule,
│   │                  RecordingCoverageRule
│   ├── mapper         BatchMapper, SummaryMapper, DashboardMapper
│   ├── services       somente *Service (@Service): UploadService, BatchCreationService, BlobStorageService,
│   │   │              TempFileService, SoftwareService, RecordingStateService, BatchStatusService,
│   │   │              BatchQueryService, HistoryService, AnalysisProcessingService, AnalysisPersistenceService
│   │   ├── dashboard  DashboardService, ConsolidationService
│   │   └── diagnosis  DiagnosisService, CoverageService
│   ├── utils          utilitários estáticos, sem estado
│   │   ├── jfr        EventView, JfrEvents, JfrFields, JfrEventTypes, StackFrames, FrameCounter,
│   │   │   │          SeriesDownsampler, Stats, JvmArgsParser, JmcMessageFormatter
│   │   │   └── extractors  MetadataExtractor, CpuExtractor, HeapExtractor, GcExtractor, ThreadExtractor,
│   │   │                   LockExtractor, HotMethodExtractor, AllocationExtractor, ExceptionExtractor,
│   │   │                   IoExtractor, LeakExtractor, ThreadCpuExtractor, SettingsExtractor
│   │   └── diagnosis  AppFrames, Diagnoses, HealthScoreCalculator
│   ├── enums          AnalysisStatus, BatchStatus, MetricType, LockType, IoType, FindingSeverity, Priority,
│   │                  DiagnosisCategory, CoverageStatus, CoverageDomain, Environment, ErrorCode
│   ├── entity         entidades JPA (§4)
│   └── repository     Spring Data JPA
├── mcp                só package-info.java (WIP)
└── ia                 só package-info.java (WIP)
```

**Regras de organização (D-18)**:
- `properties` só tem `*Properties`; `config` só tem `@Configuration`.
- `dto`, `exceptions`, `components` e `mapper` ficam no mesmo nível de `controllers`.
- Toda classe com `Exception` no nome que seja uma exceção fica em `exceptions`. O `ApiExceptionHandler` é um handler e fica em `handlers`.
- `services` só tem classes `*Service` anotadas com `@Service`.
- Toda classe `@Component` fica em `components`; todo `*Mapper` fica em `mapper`.
- Records internos (modelos de extração, diagnóstico, dashboard e processamento) ficam em subpacotes de `dto`; utilitários estáticos ficam em `utils`.
- Subpacotes por assunto são permitidos (`jfr`, `diagnosis`, `dashboard`, `processing`).

Ver [[08 Decisões e questões#D-18 — Organização de pacotes]].

## Componentes do `system`

```mermaid
flowchart TB
    AC[AnalysisController] --> US[UploadService]
    AC --> DS[DashboardService]
    RC[RecordingController] --> DS
    US --> JS[BlobStorageService]
    AP --> JS
    JS -- S3 --> BL[(SeaweedFS)]
    US --> REPO[(repositories)]
    US -- "após commit" --> AP[AnalysisProcessingService]
    AP --> JR[JfrReader]
    JR --> EX["Extratores<br/>CPU · Heap · GC · Threads · Locks<br/>HotMethods · Alocação · Exceções · I/O · Regras"]
    AP --> DG[DiagnosisService]
    DG --> RULES["Regras próprias<br/>+ traduções JMC"]
    AP --> CV[CoverageService]
    AP --> REPO
    AP --> BS[BatchStatusService]
    DS --> AF[AppFrameResolver]
    DS --> CS[ConsolidationService]
    DS --> REPO
```

| Componente             | Responsabilidade                                                                                                                                              |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `UploadService`        | Valida o lote, faz streaming para um temporário calculando o SHA-256, envia ao blob via `BlobStorageService`, cria `analysis_batch` e `jfr_recording` e agenda o processamento **depois do commit** da transação. |
| `BlobStorageService`   | Fala com o SeaweedFS pela API S3: garante o bucket, deduplica por `HeadObject`, envia e baixa `recordings/<sha256>.jfr`. Ver [[09 Armazenamento blob]].      |
| `AnalysisProcessingService`    | Roda no executor limitado: marca `PROCESSING`, chama o `JfrReader`, persiste os resultados numa transação e marca `COMPLETED` ou `FAILED`.                    |
| `JfrReader`            | Carrega a gravação com `JfrLoaderToolkit` e repassa os `IItemCollection` aos extratores.                                                                      |
| Extratores             | Um por domínio. Cada um é uma função pura `IItemCollection → resultado`, fácil de testar.                                                                     |
| `BatchStatusService`   | Recalcula o `BatchStatus` (ver [[03 Requisitos#RF-03 — Processamento assíncrono]]).                                                                           |
| `DashboardService`     | Monta a resposta por gravação.                                                                                                                                |
| `ConsolidationService` | Agrega as gravações de um lote (ver [[04 Extração JFR#Consolidação do lote]]).                                                                                |
| `DiagnosisService`     | Roda o catálogo de regras próprias, traduz os achados do JMC e gera `diagnosis_item` e `health_score` ([[03 Requisitos#RF-13 — Diagnóstico com prioridade]]).        |
| `CoverageService`      | Monta a cobertura da gravação e o comando sugerido ([[03 Requisitos#RF-14 — Cobertura da gravação]]).                                                     |
| `AppFrameResolver`     | Na consulta, escolhe o `appFrame` a partir de `stack_frames` e dos `appPackages` do software ([[03 Requisitos#RF-15 — Localizar no código]]).              |
| `SoftwareService`      | Busca ou cria o software do upload e altera a retenção.                                                                                                      |
| `RetentionJob`         | `@Scheduled` diário: apaga lotes vencidos por software e objetos do blob sem referência (ver [[03 Requisitos#RF-11 — Software e retenção]]).                  |

## Fluxo do upload

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant AC as AnalysisController
    participant US as UploadService
    participant FS as Blob (SeaweedFS, S3)
    participant DB as PostgreSQL
    participant AP as AnalysisProcessingService (async)

    C->>AC: POST /api/v1/analyses (files[])
    AC->>US: upload(files)
    US->>US: valida quantidade, extensão, tamanho e magic FLR\0
    US->>US: streaming para temporário + SHA-256
    US->>FS: HeadObject / PutObject recordings/<sha256>.jfr
    US->>DB: INSERT batch + recordings (PENDING)
    US-->>AC: batchId
    AC-->>C: 202 Accepted
    US-)AP: agenda cada recording (após commit)
    loop por gravação, até max-concurrent em paralelo
        AP->>DB: status = PROCESSING
        AP->>FS: GetObject → temporário local
        AP->>AP: extratores + regras JMC (timeout)
        AP->>AP: diagnóstico + cobertura
        AP->>DB: grava summary, séries, top-N, achados, diagnóstico e cobertura
        AP->>DB: status = COMPLETED ou FAILED
        AP->>DB: recalcula BatchStatus
    end
```

> [!tip] Retomada depois de reinício
> Um `ApplicationRunner` busca as gravações em `PENDING` ou `PROCESSING` e as coloca de volta na fila. Ver [[03 Requisitos#RF-03 — Processamento assíncrono]].

## Pacotes WIP

> [!todo] `mcp` e `ia`
> Na v0.1.0 cada um tem só um `package-info.java` com Javadoc explicando o papel futuro:
> - `mcp`: expor as análises persistidas como tools MCP;
> - `ia`: interpretar os agregados (nunca o `.jfr` bruto) e gerar resumo e recomendações.
>
> Nenhuma task de implementação entra no SDD da v0.1.0.
