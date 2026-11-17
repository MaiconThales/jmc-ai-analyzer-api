# Spec — JMC AI Analyzer API v0.1.0

| Campo | Valor |
|---|---|
| Versão | 0.1.0 |
| Data | 2026-11-17 |
| Status | Aprovada para implementação |
| Fonte | Notas Obsidian em `jmc-ai-analyzer-api/v0.1.0/` (00 a 10) e `jmc-ai-analyzer-api/Arquivo bruto.md` |
| Plano técnico | [plan.md](plan.md) |
| Tarefas | [tasks.md](tasks.md) |

> Esta spec descreve **o que** o sistema faz e **como saber que está pronto**. O **como** está em `plan.md`.
> Em caso de dúvida durante a implementação, **perguntar ao autor** antes de decidir.

---

## 1. Objetivo

API back-end que recebe, via upload, **um ou vários arquivos `.jfr`** (Java Flight Recorder / Java Mission Control). Para cada lote enviado, ela:

1. guarda o arquivo original num **blob storage**;
2. extrai **tudo o que ajuda a diagnosticar problemas no software**;
3. gera um **diagnóstico priorizado em português**, com **onde está no código** e **o que fazer**;
4. persiste o resultado no PostgreSQL para **histórico por software**;
5. expõe os dados prontos para um **dashboard**, por arquivo e consolidado por lote.

Os pacotes `ia` e `mcp` existem na estrutura, mas ficam em **WIP**.

## 2. Glossário

| Termo | Significado |
|---|---|
| **Software** | Sistema ao qual a gravação pertence (por exemplo `billing-service`). Informado no upload. É a unidade de histórico e de retenção. |
| **Versão** | Atributo do lote (`softwareVersion`). Pode mudar a cada envio. |
| **Lote** (*batch*) | Conjunto de arquivos de um mesmo upload. Tem um `batchId`. |
| **Gravação** (*recording*) | Um arquivo `.jfr` dentro de um lote. Tem um `recordingId`. |
| **Domínio** | Grupo de métricas: CPU, heap, GC, threads, locks, hot methods, alocação, exceções, I/O, suspeitos de vazamento, CPU por thread. |
| **Top-N** | Os N itens mais relevantes de um domínio. |
| **Achado** (*finding*) | Resultado de uma regra automática do JMC. |
| **Item de diagnóstico** | Problema priorizado mostrado ao usuário: título, evidência, localização e ação. Vem de um achado do JMC ou de uma regra própria. |
| **Cobertura** | O que a gravação permite ou não diagnosticar, por domínio. |
| **appFrame** | Primeiro frame da pilha que pertence ao código do software. |
| **Consolidado** | Visão agregada das gravações `COMPLETED` de um lote. |

## 3. Escopo

### 3.1. Incluído

- Upload multipart de 1..N `.jfr` por lote, com `software` obrigatório e `softwareVersion`, `environment` e `description` opcionais.
- Arquivo original no blob storage (SeaweedFS via API S3), deduplicado por SHA-256.
- Processamento assíncrono, com status por gravação e por lote, e retomada depois de reinício.
- Extração de 13 grupos de dados, incluindo os achados das 68 regras do JMC.
- Diagnóstico com prioridade e nota de saúde, cobertura da gravação e localização no código.
- Dashboard por gravação e consolidado por lote.
- Histórico paginado, filtrável por software.
- Cadastro mínimo de software (retenção e pacotes da aplicação) e job de retenção por dias.
- Swagger UI.
- Health check.
- Docker Compose com `postgres`, `blob` e `api`, com todos os dados mapeados para pastas do host, e espaço reservado para `mcp` e `frontend`.

### 3.2. Fora do escopo (v0.1.0)

- Implementação de `ia` e `mcp`.
- Front-end.
- Autenticação e autorização.
- Eventos JFR brutos no banco.
- Endpoint de reprocessamento.
- Tendência e comparação entre lotes (prevista para a v0.2.0).
- Exportar relatório (prevista para a v0.2.0).
- Domínios de safepoint, métodos nativos, flags e container (prevista para a v0.2.0).
- Pipeline de CI.

---

## 4. Requisitos funcionais

### RF-01 — Upload de arquivos JFR

`POST /api/v1/analyses`, `multipart/form-data`.

| Campo | Obrigatório | Regra |
|---|---|---|
| `files` | sim (1..N) | arquivos `.jfr` |
| `software` | sim | *slug* `^[a-z0-9][a-z0-9-]{1,63}$`. Se não existir, é criado com a retenção padrão. |
| `softwareVersion` | não | até 64 caracteres. Se ausente, extrair do nome do jar em `javaArgs` (`app-1.4.2.jar` → `1.4.2`); se não achar, fica nulo. |
| `environment` | não | `PROD`, `HML`, `DEV` ou `LOCAL` |
| `description` | não | até 500 caracteres |

Validações, todas feitas **antes** de aceitar o lote:

| # | Regra | Erro |
|---|---|---|
| V1 | `software` presente e válido; `environment` no enum; `description` ≤ 500 | `400 INVALID_PARAMETER` |
| V2 | ao menos 1 arquivo | `400 EMPTY_UPLOAD` |
| V3 | quantidade ≤ `app.upload.max-files` (padrão 20) | `400 TOO_MANY_FILES` |
| V4 | extensão `.jfr` (sem diferenciar maiúsculas) | `400 INVALID_FILE` |
| V5 | 4 primeiros bytes = `FLR\0` | `400 INVALID_FILE` |
| V6 | arquivo ≤ `app.upload.max-file-size` (padrão 128 MB) | `413 FILE_TOO_LARGE` |
| V7 | requisição ≤ `app.upload.max-request-size` (padrão 1 GB) | `413 FILE_TOO_LARGE` |
| V8 | blob storage disponível | `503 STORAGE_UNAVAILABLE` |

- **Tudo ou nada**: se qualquer validação ou envio ao blob falhar, nada é persistido. Objetos enviados ao blob nessa requisição são apagados, a menos que o mesmo SHA-256 já existisse antes.
- **Sucesso**: `202 Accepted` com `Location: /api/v1/analyses/{batchId}` e o corpo de §6.1.

> Os limites V3, V6 e V7 são **provisórios**. Ver a tarefa de medição em `tasks.md` (T-02.3).

### RF-02 — Armazenamento do arquivo original

- O objeto vai para o bucket `app.blob.bucket` (padrão `jfr-recordings`), com a chave `recordings/<sha256>.jfr` e os metadados `original-name`, `sha256` e `uploaded-at`.
- Antes de enviar, verificar com `HeadObject`. Se o objeto já existe, não envia de novo (deduplicação).
- O banco guarda nome original (sanitizado), tamanho, SHA-256, bucket e chave.
- O cliente nunca informa nem recebe caminho, bucket ou chave.

### RF-03 — Processamento assíncrono

- Cada gravação é processada de forma independente, num executor limitado (`app.analysis.max-concurrent`, padrão 2), com timeout por gravação (`app.analysis.timeout`, padrão 10 min).
- A falha de uma gravação não interrompe as outras.
- Na inicialização, gravações `PENDING` ou `PROCESSING` voltam para a fila.

**Estados da gravação (`AnalysisStatus`)**:

| De | Para | Quando |
|---|---|---|
| — | `PENDING` | upload aceito |
| `PENDING` | `PROCESSING` | executor iniciou |
| `PROCESSING` | `COMPLETED` | extração, diagnóstico e persistência concluídos |
| `PROCESSING` | `FAILED` | `PARSE_ERROR`, `TIMEOUT`, `BLOB_NOT_FOUND`, `STORAGE_UNAVAILABLE` ou `INTERNAL_ERROR` |
| `PROCESSING` | `PENDING` | reinício da API |

**Estado do lote (`BatchStatus`)**, derivado das gravações:

| Gravações | Lote |
|---|---|
| alguma `PENDING` ou `PROCESSING` | `PROCESSING` |
| todas `COMPLETED` | `COMPLETED` |
| ≥ 1 `COMPLETED` e ≥ 1 `FAILED` | `PARTIAL` |
| todas `FAILED` | `FAILED` |

`finished_at` do lote é preenchido quando ele sai de `PROCESSING`.

### RF-04 — Extração de dados do JFR

Ausência de eventos de um domínio **não é erro**: o domínio volta com `available: false` e a gravação continua `COMPLETED`.

| # | Domínio | Eventos | Resultado |
|---|---|---|---|
| 1 | Metadados | `jdk.JVMInformation`, `jdk.OSInformation`, `jdk.CPUInformation`, `jdk.PhysicalMemory` | início, fim, duração, total de eventos, JVM (nome, versão, argumentos JVM e Java), SO, núcleos, memória física |
| 2 | CPU | `jdk.CPULoad` | séries `CPU_JVM_USER`, `CPU_JVM_SYSTEM`, `CPU_MACHINE` (0–1); média e pico da JVM e da máquina |
| 3 | Heap | `jdk.GCHeapSummary`, `jdk.GCHeapConfiguration`, `jdk.GCConfiguration` | séries `HEAP_USED`, `HEAP_COMMITTED`; heap inicial e máximo, pico, coletores young e old |
| 4 | GC | `jdk.GarbageCollection` | quantidade; pausa total, média, p95 e máxima (ms); % em pausa; top-N pausas (`gcId`, coletor, causa, início, duração) |
| 5 | Threads | `jdk.JavaThreadStatistics`, `jdk.ThreadStart`, `jdk.ThreadEnd` | séries `THREADS_ACTIVE`, `THREADS_DAEMON`; picos; criadas e encerradas |
| 6 | Locks | `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait`, `jdk.ThreadPark` | top-N por (`LockType`, classe): quantidade, tempo total e máximo, pilha |
| 7 | Hot methods | `jdk.ExecutionSample` | top-N por método do topo: amostras, %, pilha |
| 8 | Alocação | `jdk.ObjectAllocationSample` (fallback `jdk.ObjectAllocationInNewTLAB`/`OutsideTLAB`) | top-N por classe: peso (bytes), amostras, pilha; taxa (bytes/s) |
| 9 | Exceções | `jdk.JavaErrorThrow`, `jdk.JavaExceptionThrow`, `jdk.ExceptionStatistics` | total de throwables (último menos primeiro de `ExceptionStatistics`); top-N por classe: quantidade, mensagem de exemplo (≤ 500 caracteres), pilha |
| 10 | I/O | `jdk.FileRead`, `jdk.FileWrite`, `jdk.SocketRead`, `jdk.SocketWrite` | totais por `IoType`; top-N por (`IoType`, alvo): bytes, quantidade, tempo. São **operações lentas** (acima do limiar do evento). |
| 11 | Achados JMC | 68 regras do `flightrecorder.rules.jdk` | ver RF-04.1 |
| 12 | Suspeitos de vazamento | `jdk.OldObjectSample` | top-N por classe: amostras, maior idade, maior tamanho de array, pilha de alocação |
| 13 | CPU por thread | `jdk.ThreadCPULoad` | top-N por thread: CPU média e máxima |

**Limites de extração**:

| Propriedade | Padrão | Regra |
|---|---|---|
| `app.analysis.max-series-points` | 300 | séries reduzidas por janelas de tempo iguais, guardando `avg` e `max` |
| `app.analysis.persisted-top-n` | 50 | itens gravados por domínio |
| `topN` (query) | 10, de 1 a 50 | itens devolvidos pela API |
| `parser_version` | `<versão app>+jmc-<versão JMC>` | gravado em cada gravação |

**Pilha**: nos domínios 6, 7, 8, 9 e 12, guardar os **5 frames de topo** mais frequentes de cada chave, com classe, método, descritor e linha.

#### RF-04.1 — Achados do JMC

- Executar todas as regras do JMC sobre a gravação.
- Persistir `ruleId`, `topic`, nome, severidade (`OK`, `INFO`, `WARNING`), score de 0 a 100 (pode ser nulo), resumo, explicação e solução.
- Os textos são persistidos com os **placeholders preenchidos**, em **texto sanitizado** (HTML permitido: `<p>`, `<ul>`, `<li>`, `<b>`, `<code>`) e com formatação determinística (locale e fuso fixos, UTC).
- Resultados `IGNORE` não são persistidos. Resultados `NA` não viram achado, mas alimentam a cobertura (RF-14).

### RF-05 — Dashboard por gravação

`GET /api/v1/recordings/{recordingId}/dashboard?topN=10` devolve a gravação completa, no formato de §6.3.

- Gravação inexistente: `404 NOT_FOUND`.
- Gravação ainda não `COMPLETED`: `409 NOT_READY` com o status atual.
- Gravação `FAILED`: `409 NOT_READY` com `errorCode` e `errorMessage`.

### RF-06 — Dashboard consolidado do lote

`GET /api/v1/analyses/{batchId}/dashboard?topN=10` agrega só as gravações `COMPLETED`. É calculado na consulta, sem tabela própria. Se nenhuma gravação estiver `COMPLETED`, a resposta é `409 NOT_READY`.

| Bloco | Regra de consolidação |
|---|---|
| Metadados | quantidade de gravações, duração somada, eventos somados, versões de JVM distintas |
| CPU | média ponderada pela duração; pico máximo |
| Heap | pico máximo; maior heap máximo |
| GC | coletas e pausa total somadas; pausa máxima global; p95 = maior p95 (aproximação); top-N de pausas mesclado |
| Threads | pico máximo; criadas e encerradas somadas |
| Locks, hot methods, alocação, exceções, I/O, vazamento | soma por chave e reordenação; % dos hot methods recalculado sobre o total de amostras |
| CPU por thread | máximo por nome de thread |
| Achados | por `ruleId`: maior severidade, maior score e `recordingIds[]` |
| Diagnóstico | união por (`source`, `location`): maior prioridade e `recordingIds[]` |
| Nota de saúde | a menor entre as gravações |
| Cobertura | por domínio, a pior situação |
| Séries | **não mescladas**: uma por gravação, indexada por `recordingId` |
| Comparativo | uma linha por gravação: duração, CPU média, pausa máxima de GC, nota de saúde, itens `ALTA` |
| Exclusões | `includedRecordings[]` e `excludedRecordings[]` (as `FAILED`) |

### RF-07 — Status do lote

`GET /api/v1/analyses/{batchId}` devolve software, versão, ambiente, descrição, status, datas e, para cada gravação, nome, tamanho, status, erro (se houver) e início e fim do processamento.

### RF-08 — Histórico

`GET /api/v1/analyses?software=&page=0&size=20&sort=createdAt,desc` devolve uma lista paginada (`size` ≤ 100) de lotes, com software, versão, ambiente, descrição, data, quantidade de arquivos, status, achados `WARNING`, nota de saúde e itens `ALTA`.

### RF-09 — Health check

`GET /actuator/health` com os componentes `db` e `blobStorage` (`HeadBucket` responde).

### RF-10 — Erros padronizados

Os erros usam `application/problem+json` (RFC 9457), com `type`, `title`, `status`, `detail`, `instance` e `code`. Nunca há stack trace na resposta. A tabela de códigos está em §6.6.

### RF-11 — Software e retenção

- O software é criado automaticamente no primeiro upload, com `retentionDays = app.retention.default-days` (padrão 90).
- `GET /api/v1/software` lista os softwares com retenção, `appPackages`, quantidade de lotes, último lote e última versão.
- `PATCH /api/v1/software/{name}` altera `retentionDays` (1..3650) e/ou `appPackages` (≤ 20 prefixos de pacote Java válidos).
- Um job diário (`app.retention.cron`, padrão `0 0 3 * * *`) apaga os lotes não-`PROCESSING` com `created_at < agora − retentionDays` do seu software, junto com todos os dados filhos.
- Em seguida, o job apaga do blob os objetos cujo `sha256` não é mais referenciado.
- O job registra no log, por software, quantos lotes, gravações e objetos foram removidos.

### RF-12 — Documentação da API (Swagger)

Swagger UI em `/swagger-ui.html` e OpenAPI em `/v3/api-docs`. Todos os endpoints têm `@Operation`, exemplos e respostas de erro `problem+json`.

### RF-13 — Diagnóstico com prioridade

Cada gravação `COMPLETED` tem uma lista de **itens de diagnóstico** e uma **nota de saúde**, calculadas no processamento e persistidas.

**Formato do item**:

| Campo | Descrição |
|---|---|
| `priority` | `ALTA`, `MEDIA` ou `BAIXA` |
| `category` | `MEMORIA`, `GC`, `CPU`, `THREADS`, `LOCKS`, `EXCECOES`, `IO`, `CONFIG_JVM`, `AMBIENTE`, `GRAVACAO` |
| `source` | `JMC:<ruleId>` ou `APP:<regra>` |
| `score` | 0–100 |
| `title` | título curto em português (ou o nome da regra do JMC, se não houver tradução) |
| `evidence` | números e fatos que dispararam o item |
| `location` | `{ thread?, target?, stackFrames?, appFrame? }`, ou nulo |
| `action` | o que fazer, em português (ou a solução original do JMC, se não houver tradução) |
| `domainRef` | bloco do dashboard com os dados (`gc`, `hotMethods`, `metadata`...) |

**Origem dos itens**:
- **Achados do JMC** com severidade `INFO` ou `WARNING` viram itens. A prioridade vem do score: ≥ 75 `ALTA`, 25–74 `MEDIA`, < 25 ou nulo `BAIXA`. Achados `WARNING` têm no mínimo `MEDIA`.
- **Regras próprias** (catálogo abaixo) viram itens com a prioridade e o score padrão da regra.

**Catálogo de regras próprias** (limites em `app.diagnosis.*`):

| Regra | Categoria | Dispara quando (padrão) | Prioridade | Ação sugerida |
|---|---|---|---|---|
| `HEAP_PRESSURE` | MEMORIA | pico de heap > 85% do máximo | ALTA | investigar retenção (suspeitos de vazamento) ou aumentar `-Xmx` |
| `GC_PAUSE_HIGH` | GC | pausa máxima > 200 ms ou > 5% do tempo em pausa | ALTA | revisar tamanho do heap, coletor e taxa de alocação |
| `ALLOCATION_RATE_HIGH` | MEMORIA | taxa > 1 GB/s | MEDIA | ver o top de alocação e o `appFrame` |
| `CPU_SATURATION` | CPU | máquina > 90% em > 10% das amostras | ALTA | separar a CPU da JVM da de outros processos |
| `HOT_METHOD_DOMINANT` | CPU | método com `appFrame` próprio > 20% das amostras | MEDIA | otimizar o método indicado |
| `LOCK_CONTENTION` | LOCKS | tempo bloqueado num monitor > 1% da duração | ALTA | reduzir o escopo do `synchronized` ou trocar a estrutura |
| `EXCEPTION_RATE_HIGH` | EXCECOES | > 100 throwables/s | MEDIA | evitar exceção como controle de fluxo (ver classe e frame) |
| `SLOW_REMOTE_CALL` | IO | `SocketRead` > 1 s num mesmo `host:porta` | ALTA | dependência lenta: revisar timeout e pool |
| `THREAD_GROWTH` | THREADS | série de threads ativas crescente e criadas > 2 × encerradas | MEDIA | possível vazamento de threads ou pool sem limite |
| `TIERED_STOP_LEVEL` | CONFIG_JVM | valor efetivo (última ocorrência) de `-XX:TieredStopAtLevel` < 4 | MEDIA | remover a flag (configuração de IDE que degrada a performance) |
| `ACTIVE_PROCESSOR_COUNT` | CONFIG_JVM | `-XX:ActiveProcessorCount` < núcleos reais | MEDIA | remover ou ajustar |
| `XMS_XMX_MISMATCH` | CONFIG_JVM | `-Xms` < 50% de `-Xmx` e `environment = PROD` | BAIXA | igualar para evitar redimensionamento do heap |
| `RECORDING_COVERAGE` | GRAVACAO | algum domínio `AUSENTE` ou `INSUFICIENTE` (RF-14) | BAIXA | seguir as recomendações da cobertura |

O score padrão por prioridade é `ALTA` = 80, `MEDIA` = 50 e `BAIXA` = 15. Todos são configuráveis.

**Traduções do JMC**: título e ação em português para os achados observados nos arquivos de exemplo (`Allocations.thread`, `Allocations.class`, `HighGc`, `CompareCpu`, `ManyRunningProcesses`, `GcOptions`, `DuplicateFlags`). Os demais achados usam o texto original.

**Nota de saúde**: `max(0, 100 − 20 × ALTA − 8 × MEDIA − 2 × BAIXA)`, com pesos configuráveis.

**Ordenação**: prioridade (`ALTA` → `BAIXA`), depois score decrescente.

### RF-14 — Cobertura da gravação

Para cada domínio (`CPU`, `HEAP`, `GC`, `THREADS`, `LOCKS`, `HOT_METHODS`, `ALLOCATION`, `EXCEPTIONS`, `FILE_IO`, `SOCKET_IO`, `LEAK`, `THREAD_CPU`), informar:

| Campo | Regra |
|---|---|
| `status` | `OK`, `AUSENTE` (evento desabilitado ou sem ocorrências) ou `INSUFICIENTE` (abaixo do mínimo) |
| `reason` | por exemplo "jdk.JavaExceptionThrow desabilitado" ou "220 amostras em 60 s" |
| `recommendation` | por exemplo "habilitar jdk.JavaExceptionThrow ou gravar com settings=profile" |

- **Fontes**: regras `NA` do JMC (eventos exigidos e configurações sugeridas), `jdk.ActiveSetting` e contagem de eventos.
- **Mínimos padrão**: `HOT_METHODS` ≥ 1.000 amostras; duração ≥ 5 min (fora disso, `INSUFICIENTE` com recomendação de duração).
- O bloco inclui `suggestedCommand`, por exemplo `-XX:StartFlightRecording=settings=profile,duration=10m,filename=app.jfr`.

### RF-15 — Localizar no código

- `appFrame` = o primeiro frame de `stackFrames` cuja classe começa com um dos `appPackages` do software.
- Se `appPackages` estiver vazio, `appFrame` é o primeiro frame fora de `app.diagnosis.framework-packages`. Padrão: `java.`, `javax.`, `jdk.`, `sun.`, `com.sun.`, `org.springframework.`, `org.apache.`, `io.netty.`.
- Se nenhum frame satisfizer a regra, `appFrame` é nulo.
- O cálculo é feito **na consulta**, então mudar `appPackages` vale também para análises antigas.

---

## 5. Requisitos não funcionais

| # | Requisito |
|---|---|
| RNF-01 | **Sonar**: *Sonar way*; nenhuma issue *Blocker*, *Critical* ou *Major*; duplicação ≤ 3%; *Quality Gate* aprovado no SonarQube existente. |
| RNF-02 | **Cobertura**: JaCoCo ≥ 90% de linhas **e** de branches; `mvn verify` falha abaixo disso. |
| RNF-03 | **Lombok**: `@Getter`, `@Setter`, `@NoArgsConstructor(access = PROTECTED)`, `@RequiredArgsConstructor`, `@Builder`, `@Slf4j`. Proibido `@Data` e `@EqualsAndHashCode`/`@ToString` padrão em entidades. DTOs são `record`s. |
| RNF-04 | **Memória**: upload em streaming para disco; nenhum `.jfr` carregado inteiro a partir do multipart. |
| RNF-05 | **Segurança**: sem caminhos vindos do cliente; nome original sanitizado; credenciais só por variável de ambiente; PostgreSQL e blob só na rede interna. |
| RNF-06 | **Configuração**: tudo em `app.*` via `@ConfigurationProperties` validadas, com sobrescrita por variável de ambiente. |
| RNF-07 | **Docker**: `docker compose up` sobe `postgres`, `blob` e `api` saudáveis. |
| RNF-08 | **Observabilidade**: MDC com `software`, `batchId` e `recordingId`; duração de cada processamento no log. |
| RNF-09 | **Persistência no host**: todo dado de container é *bind mount* em `${DATA_DIR}`; sem *named volumes*; nada se perde com `docker compose down -v`. |
| RNF-10 | **Dados sensíveis**: variáveis de ambiente, propriedades de sistema e lista de processos do `.jfr` nunca são persistidas nem expostas. |
| RNF-11 | **Determinismo**: o mesmo `.jfr` com o mesmo `parser_version` gera o mesmo resultado (locale e fuso fixos). |

---

## 6. Contratos HTTP

### 6.1. `POST /api/v1/analyses` → `202`

```json
{
  "batchId": "4f1c2e7a-...",
  "software": "billing-service",
  "softwareVersion": "1.4.2",
  "environment": "PROD",
  "description": "Lentidão no fechamento às 14h",
  "status": "PROCESSING",
  "recordings": [
    { "id": "a2b3...", "originalName": "app-prod-01.jfr", "sizeBytes": 73400320, "status": "PENDING" }
  ]
}
```

### 6.2. `GET /api/v1/analyses/{batchId}` → `200`

O corpo é o de §6.1, acrescido de `createdAt` e `finishedAt`. Cada gravação traz também `processingStartedAt`, `processingFinishedAt` e, quando `FAILED`, `errorCode` e `errorMessage`.

### 6.3. `GET /api/v1/recordings/{recordingId}/dashboard` → `200`

```json
{
  "recordingId": "...", "batchId": "...", "software": "billing-service", "softwareVersion": "1.4.2",
  "originalName": "app-prod-01.jfr", "parserVersion": "0.1.0+jmc-9.1.2",
  "healthScore": 62,
  "diagnosis": [
    { "priority": "MEDIA", "category": "CONFIG_JVM", "source": "APP:TIERED_STOP_LEVEL", "score": 50,
      "title": "Flag JVM limita o compilador ao C1",
      "evidence": "-XX:TieredStopAtLevel=1 aparece depois de =4; vale o último",
      "location": null,
      "action": "Remova -XX:TieredStopAtLevel=1",
      "domainRef": "metadata" }
  ],
  "coverage": {
    "items": [ { "domain": "EXCEPTIONS", "status": "AUSENTE", "reason": "...", "recommendation": "..." } ],
    "suggestedCommand": "-XX:StartFlightRecording=settings=profile,duration=10m,filename=app.jfr"
  },
  "metadata":     { "start": "...", "end": "...", "durationMs": 0, "eventCount": 0,
                    "jvm": { "name": "...", "version": "...", "jvmArgs": "...", "javaArgs": "..." },
                    "os": "...", "cpuCores": 0, "physicalMemoryBytes": 0 },
  "cpu":          { "available": true, "jvmAvg": 0, "jvmMax": 0, "machineAvg": 0, "machineMax": 0,
                    "series": { "CPU_JVM_USER": [ { "ts": "...", "avg": 0, "max": 0 } ], "CPU_JVM_SYSTEM": [], "CPU_MACHINE": [] } },
  "heap":         { "available": true, "initialBytes": 0, "maxBytes": 0, "usedPeakBytes": 0,
                    "youngCollector": "...", "oldCollector": "...", "series": { "HEAP_USED": [], "HEAP_COMMITTED": [] } },
  "gc":           { "available": true, "count": 0, "pauseTotalMs": 0, "pauseAvgMs": 0, "pauseP95Ms": 0, "pauseMaxMs": 0, "pausePct": 0,
                    "topPauses": [ { "gcId": 0, "collector": "...", "cause": "...", "start": "...", "durationMs": 0 } ] },
  "threads":      { "available": true, "peak": 0, "daemonPeak": 0, "started": 0, "ended": 0,
                    "series": { "THREADS_ACTIVE": [], "THREADS_DAEMON": [] } },
  "locks":        { "available": true, "top": [ { "type": "MONITOR_ENTER", "className": "...", "count": 0, "totalMs": 0, "maxMs": 0, "stackFrames": [], "appFrame": null } ] },
  "hotMethods":   { "available": true, "totalSamples": 0, "top": [ { "method": "...", "samples": 0, "percent": 0, "stackFrames": [], "appFrame": null } ] },
  "allocation":   { "available": true, "rateBytesPerSec": 0, "top": [ { "className": "...", "weightBytes": 0, "samples": 0, "stackFrames": [], "appFrame": null } ] },
  "exceptions":   { "available": true, "throwablesTotal": 0, "top": [ { "className": "...", "count": 0, "sampleMessage": "...", "stackFrames": [], "appFrame": null } ] },
  "io":           { "available": true, "totals": { "SOCKET_READ": { "bytes": 0, "totalMs": 0 } },
                    "top": [ { "type": "SOCKET_READ", "target": "db:5432", "bytes": 0, "count": 0, "totalMs": 0 } ] },
  "leakSuspects": { "available": true, "top": [ { "className": "...", "samples": 0, "maxAgeMs": 0, "maxArraySize": null, "stackFrames": [], "appFrame": null } ] },
  "threadCpu":    { "available": true, "top": [ { "threadName": "...", "cpuAvg": 0, "cpuMax": 0 } ] },
  "findings":     [ { "ruleId": "Allocations.class", "topic": "heap", "name": "...", "severity": "INFO", "score": 71.8,
                      "summary": "...", "explanation": "...", "solution": "..." } ]
}
```

- Formato de frame: `{ "c": "classe", "m": "método", "d": "descritor", "l": 142 }`.
- `findings` vem ordenado por severidade (`WARNING` primeiro) e depois por score decrescente (nulos por último).

### 6.4. `GET /api/v1/analyses/{batchId}/dashboard` → `200`

Mesmos blocos de §6.3, com as regras de RF-06, mais:
- `metadata.recordingCount`, `metadata.totalDurationMs`, `metadata.totalEventCount` e `metadata.jvmVersions[]`;
- `series` indexadas por `recordingId`;
- `recordingIds[]` em cada `finding` e em cada item de `diagnosis`;
- `comparison[]`;
- `includedRecordings[]` e `excludedRecordings[]`.

### 6.5. Histórico e software

- `GET /api/v1/analyses` devolve `{ "content": [ { batchId, software, softwareVersion, environment, description, createdAt, fileCount, status, warningFindings, healthScore, highPriorityItems } ], "page": { number, size, totalElements, totalPages } }`.
- `GET /api/v1/software` devolve `[ { name, retentionDays, appPackages[], batchCount, lastBatchAt, lastVersion } ]`.
- `PATCH /api/v1/software/{name}` recebe `{ "retentionDays"?: int, "appPackages"?: string[] }` e responde `200` com o software.

### 6.6. Códigos de erro

| `code` | HTTP | Quando |
|---|---|---|
| `INVALID_PARAMETER` | 400 | `software`/`environment`/`description` inválidos, `topN` fora de 1..50, `retentionDays` fora de 1..3650, `appPackages` inválido, UUID malformado |
| `EMPTY_UPLOAD` | 400 | nenhum arquivo |
| `TOO_MANY_FILES` | 400 | acima de `max-files` |
| `INVALID_FILE` | 400 | extensão ou magic number |
| `FILE_TOO_LARGE` | 413 | acima dos limites de tamanho |
| `NOT_FOUND` | 404 | lote, gravação ou software inexistente |
| `NOT_READY` | 409 | dashboard antes de `COMPLETED` |
| `STORAGE_UNAVAILABLE` | 503 | blob fora do ar no upload |
| `INTERNAL_ERROR` | 500 | erro inesperado |
| `PARSE_ERROR`, `TIMEOUT`, `BLOB_NOT_FOUND`, `STORAGE_UNAVAILABLE` | — | gravados na gravação `FAILED` |

---

## 7. Critérios de aceite

| # | Critério | Requisitos |
|---|---|---|
| CA-01 | Upload de 1 `.jfr` válido → `202`; a gravação chega a `COMPLETED` com os domínios disponíveis preenchidos. | RF-01, RF-03, RF-04 |
| CA-02 | 3 arquivos, sendo 1 corrompido → lote `PARTIAL`, 2 `COMPLETED` e 1 `FAILED/PARSE_ERROR`. | RF-03 |
| CA-03 | Extensão errada, magic inválido, arquivos demais, tamanho excedido ou `software` ausente → rejeitado sem persistir nada no banco nem no blob. | RF-01 |
| CA-04 | O dashboard por gravação traz todos os blocos de §6.3. | RF-05 |
| CA-05 | O consolidado mescla top-N, soma totais, une diagnósticos e lista séries por gravação. | RF-06 |
| CA-06 | Histórico e `.jfr` sobrevivem a `docker compose down -v` + `up`. | RNF-09 |
| CA-07 | Gravações pendentes são retomadas depois de reiniciar a API. | RF-03 |
| CA-08 | `mvn verify` passa com ≥ 90% de linhas e de branches; `mvn sonar:sonar` aprova o *Quality Gate*. | RNF-01, RNF-02 |
| CA-09 | `ia` e `mcp` contêm só `package-info.java` marcado como WIP. | §3 |
| CA-10 | `software` novo é criado com a retenção padrão; `PATCH` altera retenção e pacotes. | RF-11 |
| CA-11 | O job de retenção apaga só os lotes vencidos do software certo e só os objetos do blob sem referência. | RF-11 |
| CA-12 | O Swagger UI lista todos os endpoints com exemplos. | RF-12 |
| CA-13 | Os 3 arquivos de `examples files/` processam até `COMPLETED`. | RF-04 |
| CA-14 | Para os arquivos de exemplo: o diagnóstico contém `APP:TIERED_STOP_LEVEL`, `APP:ACTIVE_PROCESSOR_COUNT` e `JMC:Allocations.thread`; a cobertura marca `EXCEPTIONS` e `FILE_IO` como `AUSENTE` e `HOT_METHODS` como `INSUFICIENTE`. | RF-13, RF-14 |
| CA-15 | Com `appPackages` definido, `appFrame` aponta para o primeiro frame desse pacote; a mudança vale para análises antigas. | RF-15 |
| CA-16 | `environment` e `description` aparecem no status do lote e no histórico. | RF-07, RF-08 |
| CA-17 | Nenhuma variável de ambiente, propriedade de sistema ou processo do `.jfr` aparece no banco ou na API. | RNF-10 |
| CA-18 | Processar duas vezes o mesmo `.jfr` gera resultados idênticos. | RNF-11 |
