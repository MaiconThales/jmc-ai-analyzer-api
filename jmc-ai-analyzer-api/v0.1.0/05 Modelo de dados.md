---
tags: [jmc-ai-analyzer, v0-1-0, banco]
versao: 0.1.0
status: rascunho
---

# Modelo de dados

Voltar: [[00 Índice]] · Origem dos dados: [[04 Extração JFR]]

> [!info] Granularidade
> **Agregados + séries reduzidas + top-N.** Não há eventos brutos, e o `.jfr` original fica no blob storage. Ver [[08 Decisões e questões#D-02 — Granularidade da persistência]] e [[08 Decisões e questões#D-03 — Arquivo .jfr em blob storage (SeaweedFS)]].

## Diagrama

```mermaid
erDiagram
    SOFTWARE ||--o{ ANALYSIS_BATCH : possui
    ANALYSIS_BATCH ||--|{ JFR_RECORDING : contem
    JFR_RECORDING ||--o| RECORDING_SUMMARY : resume
    JFR_RECORDING ||--o{ METRIC_SAMPLE : series
    JFR_RECORDING ||--o{ GC_PAUSE : top_n
    JFR_RECORDING ||--o{ HOT_METHOD : top_n
    JFR_RECORDING ||--o{ LOCK_CONTENTION : top_n
    JFR_RECORDING ||--o{ ALLOCATION_HOTSPOT : top_n
    JFR_RECORDING ||--o{ EXCEPTION_HOTSPOT : top_n
    JFR_RECORDING ||--o{ IO_HOTSPOT : top_n
    JFR_RECORDING ||--o{ RULE_FINDING : achados
    JFR_RECORDING ||--o{ LEAK_SUSPECT : top_n
    JFR_RECORDING ||--o{ THREAD_CPU : top_n
    JFR_RECORDING ||--o{ DIAGNOSIS_ITEM : diagnostico
    JFR_RECORDING ||--o{ COVERAGE_ITEM : cobertura

    SOFTWARE {
        uuid id PK
        varchar name UK
        int retention_days
        text app_packages
        timestamptz created_at
        timestamptz updated_at
    }
    ANALYSIS_BATCH {
        uuid id PK
        uuid software_id FK
        varchar software_version
        varchar environment
        varchar description
        varchar status
        int file_count
        timestamptz created_at
        timestamptz finished_at
    }
    JFR_RECORDING {
        uuid id PK
        uuid batch_id FK
        varchar original_name
        varchar blob_bucket
        varchar blob_key
        char64 sha256
        bigint size_bytes
        varchar status
        varchar error_code
        text error_message
        varchar parser_version
        timestamptz recording_start
        timestamptz recording_end
        bigint duration_ms
        bigint event_count
        varchar jvm_name
        varchar jvm_version
        text jvm_args
        text java_args
        varchar os_name
        int cpu_cores
        bigint physical_memory_bytes
        timestamptz processing_started_at
        timestamptz processing_finished_at
        timestamptz created_at
    }
    RECORDING_SUMMARY {
        uuid recording_id PK
        numeric cpu_jvm_avg
        numeric cpu_jvm_max
        numeric cpu_machine_avg
        numeric cpu_machine_max
        bigint heap_initial_bytes
        bigint heap_max_bytes
        bigint heap_used_peak_bytes
        varchar gc_young_collector
        varchar gc_old_collector
        int gc_count
        numeric gc_pause_total_ms
        numeric gc_pause_avg_ms
        numeric gc_pause_p95_ms
        numeric gc_pause_max_ms
        numeric gc_pause_pct
        int thread_peak
        int thread_daemon_peak
        int threads_started
        int threads_ended
        bigint throwables_total
        numeric allocation_rate_bytes_s
        bigint execution_samples_total
        varchar domains_available
        int health_score
        text suggested_command
    }
    METRIC_SAMPLE {
        bigint id PK
        uuid recording_id FK
        varchar metric
        timestamptz ts
        numeric avg_value
        numeric max_value
    }
    GC_PAUSE {
        bigint id PK
        uuid recording_id FK
        int rank
        bigint gc_id
        varchar collector
        varchar cause
        timestamptz start_time
        numeric duration_ms
    }
    HOT_METHOD {
        bigint id PK
        uuid recording_id FK
        int rank
        text method_signature
        bigint samples
        numeric percent
        jsonb stack_frames
    }
    LOCK_CONTENTION {
        bigint id PK
        uuid recording_id FK
        int rank
        varchar lock_type
        varchar class_name
        bigint event_count
        numeric total_ms
        numeric max_ms
        jsonb stack_frames
    }
    ALLOCATION_HOTSPOT {
        bigint id PK
        uuid recording_id FK
        int rank
        varchar class_name
        bigint weight_bytes
        bigint samples
        jsonb stack_frames
    }
    EXCEPTION_HOTSPOT {
        bigint id PK
        uuid recording_id FK
        int rank
        varchar exception_class
        bigint event_count
        varchar sample_message
        jsonb stack_frames
    }
    IO_HOTSPOT {
        bigint id PK
        uuid recording_id FK
        int rank
        varchar io_type
        text target
        bigint bytes
        bigint event_count
        numeric total_ms
    }
    RULE_FINDING {
        bigint id PK
        uuid recording_id FK
        varchar rule_id
        varchar rule_name
        varchar topic
        varchar severity
        numeric score
        text summary
        text explanation
        text solution
    }
    LEAK_SUSPECT {
        bigint id PK
        uuid recording_id FK
        int rank
        varchar class_name
        bigint samples
        bigint max_age_ms
        bigint max_array_size
        jsonb stack_frames
    }
    THREAD_CPU {
        bigint id PK
        uuid recording_id FK
        int rank
        varchar thread_name
        numeric cpu_avg
        numeric cpu_max
    }
    DIAGNOSIS_ITEM {
        bigint id PK
        uuid recording_id FK
        varchar priority
        varchar category
        varchar source
        int score
        varchar title
        text evidence
        jsonb location
        text action
        varchar domain_ref
    }
    COVERAGE_ITEM {
        bigint id PK
        uuid recording_id FK
        varchar domain
        varchar status
        text reason
        text recommendation
    }
```

## Regras do modelo

- **Chaves**: `uuid` para `software`, `analysis_batch` e `jfr_recording`, que aparecem na API; `bigint identity` para as tabelas filhas.
- **`software.name`**: único, no formato *slug*. `retention_days` tem `CHECK (retention_days > 0)`.
- **FKs**: todas as tabelas filhas usam `ON DELETE CASCADE` a partir de `jfr_recording`, e `jfr_recording` a partir de `analysis_batch`. É isso que o job de retenção usa ([[03 Requisitos#RF-11 — Software e retenção]]). `analysis_batch.software_id` usa `ON DELETE RESTRICT`: um software não é apagado na v0.1.0.
- **Enums**: gravados como `varchar` com `@Enumerated(EnumType.STRING)` e `CHECK` no banco:
  - `AnalysisStatus`: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`
  - `BatchStatus`: `PROCESSING`, `COMPLETED`, `PARTIAL`, `FAILED`
  - `MetricType`: `CPU_JVM_USER`, `CPU_JVM_SYSTEM`, `CPU_MACHINE`, `HEAP_USED`, `HEAP_COMMITTED`, `THREADS_ACTIVE`, `THREADS_DAEMON`
  - `LockType`: `MONITOR_ENTER`, `MONITOR_WAIT`, `THREAD_PARK`
  - `IoType`: `FILE_READ`, `FILE_WRITE`, `SOCKET_READ`, `SOCKET_WRITE`
  - `Priority`: `ALTA`, `MEDIA`, `BAIXA`
  - `DiagnosisCategory`: `MEMORIA`, `GC`, `CPU`, `THREADS`, `LOCKS`, `EXCECOES`, `IO`, `CONFIG_JVM`, `AMBIENTE`, `GRAVACAO`
  - `CoverageStatus`: `OK`, `AUSENTE`, `INSUFICIENTE`
  - `Environment`: `PROD`, `HML`, `DEV`, `LOCAL`
  - `FindingSeverity`: `OK`, `INFO`, `WARNING` (as mesmas do JMC; `NA` e `IGNORE` não são persistidas)
  - `ErrorCode`: ver [[06 API#Erros]]
- **`stack_frames`**: `jsonb` com até 5 frames `{c, m, d, l}` (classe, método, descritor, linha). Não há coluna `app_frame`: ele é calculado na consulta ([[03 Requisitos#RF-15 — Localizar no código]]).
- **`app_packages`**: prefixos separados por vírgula, por exemplo `com.empresa,br.com.cliente`.
- **`health_score`**: de 0 a 100, calculado no processamento ([[10 Propostas de diagnóstico#P-01 — Diagnóstico unificado com prioridade]]).
- **`domains_available`**: lista dos domínios que tinham eventos, separada por vírgula (por exemplo `CPU,HEAP,GC`).
- **Índices**:
  - `jfr_recording(batch_id)` e `jfr_recording(status)`;
  - `jfr_recording(sha256)`, que não é único porque o mesmo arquivo pode estar em vários lotes;
  - `metric_sample(recording_id, metric, ts)`;
  - `(recording_id, rank)` em cada tabela de top-N;
  - `rule_finding(recording_id, severity)`;
  - `diagnosis_item(recording_id, priority)`;
  - `analysis_batch(software_id, created_at desc)` para o histórico filtrado e para o job de retenção;
  - `analysis_batch(created_at desc)` para o histórico geral.
- **Migrações Flyway**: `V1__create_software_batch_and_recording.sql`, `V2__create_summary_and_series.sql`, `V3__create_hotspots.sql`, `V4__create_rule_finding.sql`, `V5__create_leak_and_thread_cpu.sql`, `V6__create_diagnosis_and_coverage.sql`.
- **Gravação atômica**: summary, séries, top-N, achados e `status = COMPLETED` são gravados numa **única transação**. Se ela falhar, a gravação fica `FAILED` e nada parcial permanece.
- **Inserções em lote**: `hibernate.jdbc.batch_size=500` e `order_inserts=true`. Isso exige sequência com `allocationSize` compatível, não `IDENTITY` puro, para as tabelas filhas.

> [!question] Ajuste possível
> Se preferir não usar sequência nas tabelas filhas, dá para inserir séries e top-N com `JdbcClient` em lote e manter `IDENTITY`. Fica como decisão no SDD.
