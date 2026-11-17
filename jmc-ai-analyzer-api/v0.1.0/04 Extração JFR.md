---
tags: [jmc-ai-analyzer, v0-1-0, jfr]
versao: 0.1.0
status: rascunho
---

# Extração JFR

Voltar: [[00 Índice]] · Requisito: [[03 Requisitos#RF-04 — Extração de dados do JFR]]

## Biblioteca

- `org.openjdk.jmc:flightrecorder:9.1.2`: `JfrLoaderToolkit.loadEvents(File)` → `IItemCollection`.
- `org.openjdk.jmc:flightrecorder.rules.jdk:9.1.2`: regras automáticas. Execução com `RuleRegistry.getRules()` e `RulesToolkit.evaluateParallel(regras, itens, IPreferenceValueProvider.DEFAULT_VALUES, threads)`.

Motivo da escolha em [[08 Decisões e questões#D-01 — Parser JFR no back-end]].

> [!success] Spike executado em 2026-11-17 com os arquivos de `examples files/`
> - **Funciona**: carregou os 3 arquivos (cerca de 1,5 MB e 60 s cada, gravados no HotSpot 21 do Windows) com bytecode Java 17, executou as **68 regras** e levou cerca de 200 ms por arquivo.
> - **Memória**: cerca de 30 MB de heap após carregar um arquivo de 1,5 MB, ou seja, ~**20× o tamanho do arquivo**. É uma estimativa a confirmar com um arquivo grande.
> - **Severidades reais do JMC**: `NA`, `OK`, `INFO`, `WARNING`, `IGNORE`. **Não existe `CRITICAL`.** O modelo foi corrigido em [[05 Modelo de dados]].
> - **Mensagens com placeholders**: `getSummary()` devolve texto como `The most allocated type is likely ''{mostAllocatedType}''`. É obrigatório usar `ResultToolkit.populateMessage(result, texto, false)`.
> - **HTML nas mensagens**: explicação e solução trazem tags HTML. Persistir como texto limpo (sanitizado) e manter só `<p>`, `<ul>`, `<li>`, `<b>` e `<code>`.
> - **Locale**: números e datas saem no locale da JVM (`29,8`, `13/11/2026`). Fixar `Locale` e `TimeZone` (UTC) no processamento para o resultado ser determinístico.
> - **Score**: `result.getResult(TypedResult.SCORE)` pode ser `null` em regras informativas, como `GcOptions` e `DuplicateFlags`.
> - **Idioma**: as mensagens do JMC vêm **em inglês**. Ver a proposta de catálogo em português em [[10 Propostas de diagnóstico]].
> - **Log**: o parser avisa `Could not find field with name 'virtual'` em gravações do JDK 21+. Isso é inofensivo; reduzir o logger `org.openjdk.jmc` para `ERROR`.
> - **Gravação de JDK mais novo**: a API roda em Java 17 e lê `.jfr` do JDK 21 normalmente, porque o parser do JMC não depende da JVM que gravou.

## Domínios

> [!note] Evento ausente não é erro
> Se a gravação não tiver os eventos de um domínio (por exemplo, `jdk.JavaExceptionThrow` vem desligado no perfil padrão), o domínio volta com `available: false` e a gravação continua `COMPLETED`.

### 1. Metadados da gravação

| Dado | Fonte |
|---|---|
| início, fim, duração | menor `startTime` e maior `endTime` dos eventos |
| total de eventos | contagem do `IItemCollection` |
| JVM: nome, versão, argumentos JVM e Java | `jdk.JVMInformation` |
| sistema operacional | `jdk.OSInformation` |
| núcleos de CPU | `jdk.CPUInformation` |
| memória física | `jdk.PhysicalMemory` |

### 2. CPU — `jdk.CPULoad`

- **Séries**: `CPU_JVM_USER`, `CPU_JVM_SYSTEM`, `CPU_MACHINE` (fração de 0 a 1).
- **Resumo**: média e pico da JVM (usuário + sistema) e da máquina.

### 3. Memória / heap — `jdk.GCHeapSummary`, `jdk.GCHeapConfiguration`, `jdk.GCConfiguration`

- **Séries**: `HEAP_USED`, `HEAP_COMMITTED` (bytes).
- **Resumo**: heap inicial, heap máximo, pico de uso, coletores young e old.

### 4. GC — `jdk.GarbageCollection`

- **Resumo**: quantidade de coletas; pausa total, média, **p95** e máxima (ms); % do tempo da gravação em pausa.
- **Top-N**: maiores pausas (`gcId`, coletor, causa, início, duração).

### 5. Threads — `jdk.JavaThreadStatistics`, `jdk.ThreadStart`, `jdk.ThreadEnd`

- **Séries**: `THREADS_ACTIVE`, `THREADS_DAEMON`.
- **Resumo**: pico de threads ativas, pico de daemon, threads criadas e encerradas.

### 6. Locks — `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait`, `jdk.ThreadPark`

- **Top-N** por (`LockType`, classe do monitor/bloqueador): quantidade, tempo total, tempo máximo e pilha (ver [[#Pilha e frame da aplicação]]).

### 7. Hot methods — `jdk.ExecutionSample`

- **Top-N** por método do topo da pilha: amostras e % do total de amostras.
- Assinatura no formato `pacote.Classe.metodo(tipos)`.

### 8. Alocação — `jdk.ObjectAllocationSample` (com fallback para `jdk.ObjectAllocationInNewTLAB` e `OutsideTLAB`)

- **Top-N** por classe: peso alocado (bytes) e quantidade de amostras.
- **Resumo**: taxa de alocação (bytes/s) = peso total ÷ duração.

### 9. Exceções e erros — `jdk.JavaErrorThrow`, `jdk.JavaExceptionThrow`, `jdk.ExceptionStatistics`

- **Resumo**: total de throwables (último valor de `jdk.ExceptionStatistics` menos o primeiro).
- **Top-N** por classe: quantidade, uma mensagem de exemplo (truncada em 500 caracteres) e pilha (ver [[#Pilha e frame da aplicação]]).

### 10. I/O — `jdk.FileRead`, `jdk.FileWrite`, `jdk.SocketRead`, `jdk.SocketWrite`

- **Resumo**: bytes e tempo total por `IoType`.
- **Top-N** por (`IoType`, alvo), onde alvo é o caminho do arquivo ou `host:porta`: bytes, quantidade e tempo total.

> [!caution] Eventos com limiar
> Por padrão esses eventos só registram operações acima de 10–20 ms. O dashboard deve deixar claro que os números são **operações lentas**, não todo o I/O.

### 12. Suspeitos de vazamento — `jdk.OldObjectSample`

- **Top-N** por classe do objeto: quantidade de amostras, maior idade (tempo vivo), tamanho do array (quando houver) e a pilha de **quem alocou**.
- O cálculo do caminho até a raiz (*path to GC roots*) não entra: exige `-XX:StartFlightRecording:path-to-gc-roots=true` e fica como recomendação na cobertura.
- Origem: [[10 Propostas de diagnóstico#P-05 — Novos domínios já presentes nos arquivos|P-05a]].

### 13. CPU por thread — `jdk.ThreadCPULoad`

- **Top-N** por thread: CPU média e máxima (usuário + sistema).
- Origem: [[10 Propostas de diagnóstico#P-05 — Novos domínios já presentes nos arquivos|P-05b]].

### 11. Achados automáticos — regras do JMC

Para cada regra executada, persistir: `ruleId`, `topic` (por exemplo `heap`, `gc`, `socket_io`), nome, severidade do JMC (`OK`, `INFO`, `WARNING`), score de 0 a 100 (pode ser nulo), e resumo, explicação e solução já com os placeholders preenchidos.

- Regras com resultado `IGNORE` **não** são persistidas.
- Regras `NA` também não viram achados. Os eventos que faltaram (`IRule.getRequiredEvents()` e `IResult.suggestRecordingSettings()`) alimentam a **cobertura da gravação**, ver [[10 Propostas de diagnóstico#P-03 — Cobertura da gravação]].

> [!example] Achados reais nos arquivos de exemplo
> - `Allocations.thread` (INFO, 74): a maioria das alocações vem da thread `KafkaListenerEndpointContainer#0-0-C-1`.
> - `Allocations.class` (INFO, 71,8): o tipo mais alocado é `byte[]`, vindo de `AbstractStringBuilder.<init>(int)`.
> - `HighGc` (INFO, 26,1): a JVM ficou 100% pausada numa janela de ~130 ms.
> - `CompareCpu` / `ManyRunningProcesses`: 43% de CPU foi usada por outros processos, com 325 processos rodando.
> - `GcOptions`: GC paralelo com uma única thread de GC (efeito de `-XX:ActiveProcessorCount=1`).
> - `DuplicateFlags`: `-XX:TieredStopAtLevel` foi definido duas vezes.

## Pilha e frame da aplicação

- Para hot methods, alocação, exceções, locks e suspeitos de vazamento, guardar os **5 frames de topo** mais frequentes de cada chave, com `className`, `method`, `descriptor` e `line`.
- Formato `jsonb`: `[{"c":"com.x.PedidoService","m":"calcular","l":142}, ...]`.
- O `appFrame` é calculado na consulta. Ver [[03 Requisitos#RF-15 — Localizar no código]].

## Diagnóstico e cobertura

Depois dos extratores e das regras do JMC, o `DiagnosisService` roda o catálogo próprio sobre os resultados. Ele gera `diagnosis_item`, `coverage_item` e a `health_score`. Regras: [[10 Propostas de diagnóstico]].

A cobertura usa:
- regras `NA` do JMC;
- `jdk.ActiveSetting` (eventos habilitados e `threshold`/`period`);
- contagem de eventos por domínio. Por exemplo, menos de 1.000 `jdk.ExecutionSample` é `INSUFICIENTE`.

## Limites

| Propriedade | Padrão | Uso |
|---|---|---|
| `app.analysis.max-series-points` | 300 | Máximo de pontos por série. Usa janelas de tempo iguais, guardando `avg` e `max` de cada uma. |
| `app.analysis.persisted-top-n` | 50 | Quantos itens de top-N são **gravados** por domínio. |
| `topN` (query) | 10 (máx. 50) | Quantos itens são **devolvidos** pela API. |
| `parser_version` | versão do app + versão do JMC | Gravado em cada gravação, para saber com que versão foi extraída. |

> [!tip] Por que gravar 50 e mostrar 10?
> Ao consolidar um lote, um item que é o 12º num arquivo pode virar o 3º no total. Gravar mais itens do que se mostra reduz esse erro de mesclagem.

## Consolidação do lote

Só entram as gravações `COMPLETED`. Requisito: [[03 Requisitos#RF-06 — Dashboard consolidado do lote]].

| Domínio | Regra |
|---|---|
| Metadados | quantidade de gravações, duração somada, eventos somados, versões de JVM distintas |
| CPU | média **ponderada pela duração** e pico máximo |
| Heap | pico máximo e heap máximo configurado (o maior) |
| GC | coletas somadas, pausa total somada, pausa máxima global, p95 = maior p95 entre as gravações *(aproximação)*, top-N de pausas mesclado |
| Threads | pico máximo e soma de criadas e encerradas |
| Locks, hot methods, alocação, exceções, I/O | **soma por chave** e reordenação; o % dos hot methods é recalculado sobre o total de amostras |
| Achados | por `ruleId`: a maior severidade, o maior score e a lista de `recordingId` afetados |
| Séries temporais | **não são mescladas**: uma série por gravação, identificada pelo `recordingId` |
| Suspeitos de vazamento, CPU por thread | soma de amostras por classe e máximo de CPU por nome de thread |
| Diagnóstico | itens unidos por (`source`, `location`), com a maior prioridade e `recordingIds[]`; a nota de saúde do lote é a menor entre as gravações |
| Cobertura | por domínio, a pior situação entre as gravações |
| Comparativo | uma linha por gravação: duração, CPU média, pausa máxima de GC, nota de saúde e itens `ALTA` |

O consolidado é **calculado na consulta**, a partir das tabelas por gravação. Não existe tabela própria para ele. Ver [[08 Decisões e questões#D-04 — Consolidado calculado na consulta]].
