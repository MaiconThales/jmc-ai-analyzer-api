---
tags: [jmc-ai-analyzer, v0-1-0, api]
versao: 0.1.0
status: rascunho
---

# API

Voltar: [[00 Índice]] · Requisitos: [[03 Requisitos]]

## Endpoints

| Método | Caminho | Descrição | Requisito |
|---|---|---|---|
| `POST` | `/api/v1/analyses` | Upload de 1..N `.jfr` → `202` | [[03 Requisitos#RF-01 — Upload de arquivos JFR\|RF-01]] |
| `GET` | `/api/v1/analyses` | Histórico paginado | [[03 Requisitos#RF-08 — Histórico\|RF-08]] |
| `GET` | `/api/v1/analyses/{batchId}` | Status do lote | [[03 Requisitos#RF-07 — Status do lote\|RF-07]] |
| `GET` | `/api/v1/analyses/{batchId}/dashboard?topN=10` | Dashboard consolidado | [[03 Requisitos#RF-06 — Dashboard consolidado do lote\|RF-06]] |
| `GET` | `/api/v1/recordings/{recordingId}/dashboard?topN=10` | Dashboard da gravação | [[03 Requisitos#RF-05 — Dashboard por gravação\|RF-05]] |
| `GET` | `/api/v1/software` | Lista de softwares, com retenção e último lote | [[03 Requisitos#RF-11 — Software e retenção\|RF-11]] |
| `PATCH` | `/api/v1/software/{name}` | Altera `retentionDays` e `appPackages` | [[03 Requisitos#RF-11 — Software e retenção\|RF-11]] |
| `GET` | `/actuator/health` | Saúde | [[03 Requisitos#RF-09 — Health check\|RF-09]] |
| `GET` | `/swagger-ui.html` · `/v3/api-docs` | Swagger | [[03 Requisitos#RF-12 — Documentação da API (Swagger)\|RF-12]] |

## POST /api/v1/analyses

```http
POST /api/v1/analyses
Content-Type: multipart/form-data; boundary=---x

---x
Content-Disposition: form-data; name="software"

billing-service
---x
Content-Disposition: form-data; name="softwareVersion"

1.4.2
---x
Content-Disposition: form-data; name="environment"

PROD
---x
Content-Disposition: form-data; name="description"

Lentidão no fechamento às 14h
---x
Content-Disposition: form-data; name="files"; filename="app-prod-01.jfr"
...
---x
Content-Disposition: form-data; name="files"; filename="app-prod-02.jfr"
...
```

Resposta `202 Accepted`, com `Location: /api/v1/analyses/{batchId}`:

```json
{
  "batchId": "4f1c2e7a-...",
  "software": "billing-service",
  "softwareVersion": "1.4.2",
  "status": "PROCESSING",
  "recordings": [
    { "id": "a2b3...", "originalName": "app-prod-01.jfr", "sizeBytes": 73400320, "status": "PENDING" },
    { "id": "c9d0...", "originalName": "app-prod-02.jfr", "sizeBytes": 51200000, "status": "PENDING" }
  ]
}
```

## GET /api/v1/analyses/{batchId}

```json
{
  "batchId": "4f1c2e7a-...",
  "status": "PARTIAL",
  "createdAt": "2026-11-17T14:02:11Z",
  "finishedAt": "2026-11-17T14:03:40Z",
  "recordings": [
    { "id": "a2b3...", "originalName": "app-prod-01.jfr", "sizeBytes": 73400320, "status": "COMPLETED",
      "processingStartedAt": "...", "processingFinishedAt": "..." },
    { "id": "c9d0...", "originalName": "app-prod-02.jfr", "sizeBytes": 51200000, "status": "FAILED",
      "errorCode": "PARSE_ERROR", "errorMessage": "Arquivo JFR truncado ou corrompido" }
  ]
}
```

## GET /api/v1/recordings/{recordingId}/dashboard

Formato da resposta, com valores ilustrativos:

```json
{
  "recordingId": "a2b3...",
  "batchId": "4f1c...",
  "originalName": "app-prod-01.jfr",
  "parserVersion": "0.1.0+jmc-9.1.2",
  "healthScore": 62,
  "diagnosis": [
    { "priority": "MEDIA", "category": "CONFIG_JVM", "source": "APP:TIERED_STOP_LEVEL", "score": 60,
      "title": "Flag JVM limita o compilador ao C1",
      "evidence": "-XX:TieredStopAtLevel=1 aparece depois de =4; vale o último",
      "location": null,
      "action": "Remova -XX:TieredStopAtLevel=1 (configuração de IDE para subir rápido)",
      "domainRef": "metadata" },
    { "priority": "MEDIA", "category": "MEMORIA", "source": "JMC:Allocations.thread", "score": 74,
      "title": "Thread com maior volume de alocação",
      "evidence": "KafkaListenerEndpointContainer#0-0-C-1 concentra as alocações; tipo byte[]",
      "location": { "thread": "KafkaListenerEndpointContainer#0-0-C-1",
                    "appFrame": { "c": "com.empresa.pedido.PedidoListener", "m": "onMessage", "l": 57 } },
      "action": "Revisar montagem e serialização das mensagens no listener",
      "domainRef": "allocation" }
  ],
  "coverage": {
    "items": [
      { "domain": "EXCEPTIONS", "status": "AUSENTE", "reason": "jdk.JavaExceptionThrow desabilitado",
        "recommendation": "Habilitar jdk.JavaExceptionThrow ou gravar com settings=profile" },
      { "domain": "HOT_METHODS", "status": "INSUFICIENTE", "reason": "220 amostras em 60 s",
        "recommendation": "Gravar por mais tempo ou com settings=profile" }
    ],
    "suggestedCommand": "-XX:StartFlightRecording=settings=profile,duration=10m,filename=app.jfr"
  },
  "metadata": {
    "start": "...", "end": "...", "durationMs": 600000, "eventCount": 1843221,
    "jvm": { "name": "OpenJDK 64-Bit Server VM", "version": "17.0.12", "jvmArgs": "-Xmx2g ...", "javaArgs": "..." },
    "os": "Linux", "cpuCores": 8, "physicalMemoryBytes": 17179869184
  },
  "cpu":         { "available": true, "jvmAvg": 0.41, "jvmMax": 0.97, "machineAvg": 0.55, "machineMax": 1.0,
                   "series": { "CPU_JVM_USER": [{ "ts": "...", "avg": 0.3, "max": 0.5 }], "CPU_JVM_SYSTEM": [], "CPU_MACHINE": [] } },
  "heap":        { "available": true, "initialBytes": 0, "maxBytes": 0, "usedPeakBytes": 0, "youngCollector": "G1New", "oldCollector": "G1Old",
                   "series": { "HEAP_USED": [], "HEAP_COMMITTED": [] } },
  "gc":          { "available": true, "count": 412, "pauseTotalMs": 3120.5, "pauseAvgMs": 7.6, "pauseP95Ms": 21.0, "pauseMaxMs": 180.2, "pausePct": 0.52,
                   "topPauses": [{ "gcId": 301, "collector": "G1Old", "cause": "Allocation Failure", "start": "...", "durationMs": 180.2 }] },
  "threads":     { "available": true, "peak": 212, "daemonPeak": 40, "started": 980, "ended": 940, "series": { "THREADS_ACTIVE": [], "THREADS_DAEMON": [] } },
  "locks":       { "available": true, "top": [{ "type": "MONITOR_ENTER", "className": "com.x.Cache", "count": 3021, "totalMs": 8123, "maxMs": 450, "stackFrames": [], "appFrame": null }] },
  "hotMethods":  { "available": true, "totalSamples": 58000, "top": [{ "method": "com.x.Parser.parse(String)", "samples": 7400, "percent": 12.76 }] },
  "allocation":  { "available": true, "rateBytesPerSec": 5.2e8, "top": [{ "className": "byte[]", "weightBytes": 0, "samples": 0 }] },
  "exceptions":  { "available": true, "throwablesTotal": 15230, "top": [{ "className": "java.io.IOException", "count": 820, "sampleMessage": "...", "stackFrames": [], "appFrame": null }] },
  "io":          { "available": true, "totals": { "FILE_READ": { "bytes": 0, "totalMs": 0 } },
                   "top": [{ "type": "SOCKET_READ", "target": "db:5432", "bytes": 0, "count": 0, "totalMs": 0 }] },
  "leakSuspects": { "available": true, "top": [{ "className": "java.util.HashMap$Node", "samples": 12, "maxAgeMs": 58000, "maxArraySize": null,
                    "stackFrames": [{ "c": "...", "m": "...", "l": 0 }], "appFrame": { "c": "...", "m": "...", "l": 0 } }] },
  "threadCpu":   { "available": true, "top": [{ "threadName": "KafkaListenerEndpointContainer#0-0-C-1", "cpuAvg": 0.21, "cpuMax": 0.64 }] },
  "findings": [
    { "ruleId": "Allocations.class", "topic": "heap", "name": "Allocated Classes", "severity": "INFO", "score": 71.8,
      "summary": "...", "explanation": "...", "solution": "..." }
  ]
}
```

> [!note] Pilhas nos itens de top-N
> Os itens de `locks`, `hotMethods`, `allocation`, `exceptions` e `leakSuspects` trazem `stackFrames[]` (até 5) e `appFrame`, que pode ser nulo. Os exemplos acima foram resumidos.

> [!note] Ordem do diagnóstico
> `diagnosis` vem ordenado por prioridade (`ALTA` → `BAIXA`) e depois por `score` decrescente.

> [!note] Ordem dos achados
> Os `findings` vêm ordenados por severidade (`WARNING` primeiro) e depois por `score` decrescente (nulos por último).

## GET /api/v1/analyses/{batchId}/dashboard

Usa os mesmos blocos do dashboard por gravação, com estas diferenças (regras em [[04 Extração JFR#Consolidação do lote]]):

- `metadata` traz `recordingCount`, `totalDurationMs`, `totalEventCount` e `jvmVersions[]`;
- as `series` passam a ser `{ "<recordingId>": { "CPU_JVM_USER": [...] } }`;
- cada `finding` e cada item de `diagnosis` trazem `recordingIds[]`;
- `healthScore` é a menor nota entre as gravações, e `coverage` mostra a pior situação por domínio;
- há um bloco extra `comparison[]`, com uma linha por gravação;
- `includedRecordings[]` e `excludedRecordings[]` mostram as gravações `FAILED` que ficaram fora.

## GET /api/v1/analyses (histórico)

Parâmetros: `software` (opcional) e os do Spring Data: `page`, `size` (máximo 100) e `sort` (padrão `createdAt,desc`).

```json
{
  "content": [
    { "batchId": "...", "software": "billing-service", "softwareVersion": "1.4.2", "createdAt": "...",
      "environment": "PROD", "description": "Lentidão no fechamento às 14h",
      "fileCount": 2, "status": "PARTIAL", "warningFindings": 3, "healthScore": 62, "highPriorityItems": 0 }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
}
```

> [!tip] Paginação estável
> Use `PagedModel` (ou `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`) para não expor a serialização interna de `PageImpl`.

## Software

`GET /api/v1/software`:

```json
[
  { "name": "billing-service", "retentionDays": 90, "appPackages": ["com.empresa"], "batchCount": 12,
    "lastBatchAt": "2026-11-17T14:02:11Z", "lastVersion": "1.4.2" }
]
```

`PATCH /api/v1/software/billing-service`, com o corpo `{ "retentionDays": 30, "appPackages": ["com.empresa", "br.com.cliente"] }` (os dois campos são opcionais), responde `200` com o software atualizado.

Validações (fora disso, `INVALID_PARAMETER`):
- `retentionDays` de 1 a 3650;
- `appPackages` com até 20 prefixos no formato de pacote Java.

## Erros

Formato `application/problem+json` (RFC 9457):

```json
{
  "type": "about:blank",
  "title": "Arquivo inválido",
  "status": 400,
  "detail": "O arquivo 'dump.bin' não é uma gravação JFR válida.",
  "instance": "/api/v1/analyses",
  "code": "INVALID_FILE"
}
```

| `code` | HTTP | Quando |
|---|---|---|
| `EMPTY_UPLOAD` | 400 | nenhum arquivo enviado |
| `TOO_MANY_FILES` | 400 | mais arquivos que `max-files` |
| `INVALID_FILE` | 400 | extensão ou magic number inválidos |
| `INVALID_PARAMETER` | 400 | `software` ausente ou inválido, `topN` fora de 1..50, `retentionDays` fora de 1..3650, UUID malformado |
| `FILE_TOO_LARGE` | 413 | tamanho acima do limite |
| `NOT_FOUND` | 404 | lote, gravação ou software inexistente |
| `NOT_READY` | 409 | dashboard pedido antes de `COMPLETED` |
| `STORAGE_UNAVAILABLE` | 503 | blob storage fora do ar no upload |
| `INTERNAL_ERROR` | 500 | erro inesperado, sem stack trace |
| `PARSE_ERROR` | — | gravado na gravação `FAILED` |
| `TIMEOUT` | — | gravado na gravação `FAILED` |
| `BLOB_NOT_FOUND` | — | gravado na gravação `FAILED` (objeto sumiu do blob) |

> [!example] Documentação interativa
> Swagger UI incluído na v0.1.0 (`springdoc-openapi` 3.x). Ver [[03 Requisitos#RF-12 — Documentação da API (Swagger)]].
