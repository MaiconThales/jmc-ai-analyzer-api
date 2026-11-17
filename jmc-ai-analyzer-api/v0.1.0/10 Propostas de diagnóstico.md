---
tags: [jmc-ai-analyzer, v0-1-0, diagnostico]
versao: 0.1.0
status: aprovado
---

# Propostas de diagnóstico

Voltar: [[00 Índice]] · Base: spike com os arquivos de `examples files/` ([[04 Extração JFR#Biblioteca]])

> [!success] Aprovado em 2026-11-17 — ver [[08 Decisões e questões#D-16 — Propostas de diagnóstico aprovadas|D-16]]
> **v0.1.0**: P-01, P-02, P-03, P-04, P-05a, P-05b e P-07, já refletidas em [[03 Requisitos]], [[04 Extração JFR]], [[05 Modelo de dados]] e [[06 API]].
> **v0.2.0**: P-06, P-08 e P-05c a P-05e.

> [!abstract] Objetivo
> Hoje o dashboard mostra **métricas**. Para cumprir "abrir o arquivo e ver o que precisa ser feito para arrumar", ele precisa mostrar **problemas priorizados**, cada um com **onde está no código** e **o que fazer**. As propostas abaixo cobrem esse caminho. Esta nota é a referência detalhada das regras de diagnóstico.

## Resumo e recomendação

| #    | Proposta                                   | Ganho                                                                          | Esforço | Recomendo                                |
| ---- | ------------------------------------------ | ------------------------------------------------------------------------------ | ------- | ---------------------------------------- |
| P-01 | Diagnóstico unificado com prioridade       | O dashboard abre com a lista "o que arrumar primeiro"                          | Médio   | **v0.1.0**                               |
| P-02 | Catálogo de regras próprias em português   | Ações claras em pt-BR, com limites ajustáveis                                  | Médio   | **v0.1.0**                               |
| P-03 | Cobertura da gravação                      | Avisa quando o `.jfr` não tem dados para diagnosticar e diz como gravar melhor | Baixo   | **v0.1.0**                               |
| P-04 | Localizar no código (pilha + "seu código") | Leva direto à classe, ao método e à linha                                      | Médio   | **v0.1.0**                               |
| P-05 | Novos domínios já presentes nos arquivos   | Vazamento de memória, CPU por thread, safepoints                               | Médio   | **v0.1.0** (P-05a e P-05b), resto depois |
| P-06 | Tendência por software                     | Mostra se a correção funcionou entre um envio e o seguinte                     | Médio   | v0.2.0                                   |
| P-07 | Contexto do upload (ambiente + descrição)  | Dá contexto ao histórico ("prod, lentidão às 14h")                             | Baixo   | **v0.1.0**                               |
| P-08 | Exportar relatório em Markdown             | Anexar em ticket ou PR                                                         | Baixo   | v0.2.0                                   |

---

## P-01 — Diagnóstico unificado com prioridade

Um bloco `diagnosis[]` no **topo** dos dashboards (por gravação e consolidado). Ele junta os achados do JMC e as regras próprias (P-02) num formato único:

| Campo | Exemplo |
|---|---|
| `priority` | `ALTA` · `MEDIA` · `BAIXA` |
| `category` | `MEMORIA`, `GC`, `CPU`, `THREADS`, `LOCKS`, `EXCECOES`, `IO`, `CONFIG_JVM`, `AMBIENTE`, `GRAVACAO` |
| `title` | "Flag JVM limita o compilador ao C1" |
| `evidence` | "`-XX:TieredStopAtLevel=1` aparece depois de `=4`; vale o último" |
| `location` | thread, classe, método e linha (P-04), ou `host:porta` |
| `action` | "Remova `-XX:TieredStopAtLevel=1` (é configuração de IDE para subir rápido)" |
| `source` | `JMC:DuplicateFlags` ou `APP:TIERED_STOP_LEVEL` |
| `score` | 0–100 |
| `domainRef` | seção do dashboard com os dados (`gc`, `hotMethods`...) |

- **Prioridade**: `score ≥ 75` → `ALTA`, `25–74` → `MEDIA`, `< 25` → `BAIXA`. Regras próprias podem forçar a prioridade.
- **Nota de saúde**: um índice de 0 a 100 por gravação e por lote, que soma penalidades por prioridade. Serve de selo no histórico.
- **Persistência**: tabela `diagnosis_item`, gravada no processamento, para o histórico guardar o que foi diagnosticado naquela versão do parser.

> [!example] Como ficaria com os seus arquivos de exemplo
> 1. **MÉDIA · CONFIG_JVM** — `-XX:ActiveProcessorCount=1`: o GC roda com 1 thread e a JVM se comporta como se houvesse 1 núcleo. *Ação*: remover a flag ou usar o número real de núcleos.
> 2. **MÉDIA · CONFIG_JVM** — `-XX:TieredStopAtLevel` duplicado (`=4` e `=1`): vale `=1`, então só o compilador C1 roda e o código fica mais lento. *Ação*: manter só uma flag; em produção, nenhuma.
> 3. **MÉDIA · MEMORIA** — a thread que mais aloca é `KafkaListenerEndpointContainer#0-0-C-1` (score 74 no JMC), e o tipo mais alocado é `byte[]`, via `AbstractStringBuilder.<init>`. *Ação*: revisar a montagem e serialização de mensagens no listener (concatenação de String, `toString()` de payloads grandes em log).
> 4. **MÉDIA · AMBIENTE** — 43% da CPU da máquina foi usada por outros processos (325 rodando). *Ação*: as medições dessa gravação podem estar distorcidas; gravar num ambiente isolado.
> 5. **BAIXA · GRAVACAO** — sem eventos de exceção nem de I/O de arquivo, e só ~230 amostras de CPU em 60 s. *Ação*: ver P-03.

## P-02 — Catálogo de regras próprias em português

O JMC devolve as mensagens em inglês e não conhece o contexto de cada software. Um catálogo em `system.components.diagnosis.rules` (uma classe por regra, todas pequenas e testáveis) usa os dados já extraídos.

| Regra | Dispara quando (padrão configurável) | Ação sugerida |
|---|---|---|
| `HEAP_PRESSURE` | pico de heap > 85% do máximo | investigar retenção (P-05a) ou aumentar `-Xmx` |
| `GC_PAUSE_HIGH` | pausa máxima > 200 ms ou > 5% do tempo em pausa | revisar tamanho do heap, coletor, taxa de alocação |
| `ALLOCATION_RATE_HIGH` | > 1 GB/s | ver top de alocação com a pilha (P-04) |
| `CPU_SATURATION` | máquina > 90% por > 10% do tempo | separar CPU da JVM e de outros processos |
| `HOT_METHOD_DOMINANT` | um método da aplicação com > 20% das amostras | otimizar o método indicado |
| `LOCK_CONTENTION` | tempo bloqueado > 1% da duração num mesmo monitor | reduzir o escopo do `synchronized` ou trocar a estrutura |
| `EXCEPTION_RATE_HIGH` | > 100 throwables/s | exceção usada para controle de fluxo; ver a classe e o frame |
| `SLOW_REMOTE_CALL` | leitura de socket > 1 s no mesmo `host:porta` | dependência lenta; revisar timeout e pool |
| `THREAD_GROWTH` | threads ativas sempre crescendo e criadas ≫ encerradas | possível vazamento de threads ou pool sem limite |
| `TIERED_STOP_LEVEL` | `-XX:TieredStopAtLevel` < 4 | remover a flag (é de IDE e degrada a performance) |
| `ACTIVE_PROCESSOR_COUNT` | `-XX:ActiveProcessorCount` < núcleos reais | remover ou ajustar |
| `XMS_XMX_MISMATCH` | `-Xms` ≪ `-Xmx` em servidor | igualar para evitar redimensionamento do heap |

Os limites ficam em `app.diagnosis.*`. Os achados do JMC mais comuns ganham **título e ação traduzidos** por `ruleId`; os demais mostram o texto original em inglês.

## P-03 — Cobertura da gravação

Um bloco `recordingCoverage` diz o que **não dá para diagnosticar** com aquele arquivo e como corrigir na próxima gravação. Fontes:
- regras do JMC com resultado `NA` (`getRequiredEvents()` e `suggestRecordingSettings()`);
- o evento `jdk.ActiveSetting`, que traz a configuração usada na gravação;
- a contagem de eventos por domínio.

| Verificação | Nos exemplos | Recomendação |
|---|---|---|
| Exceções lançadas (`jdk.JavaExceptionThrow`) | ausente | habilitar o evento no `.jfc` ou usar `settings=profile` |
| I/O de arquivo (`jdk.FileRead`/`FileWrite`) | ausente | baixar `threshold` (padrão 20 ms) |
| Amostras de CPU (`jdk.ExecutionSample`) | ~230 em 60 s (baixo) | gravar por mais tempo ou com `settings=profile` (10 ms) |
| Duração | 60 s | ao menos 5 min sob carga real |

O bloco termina com o **comando sugerido** para gravar de novo, por exemplo:
`-XX:StartFlightRecording=settings=profile,duration=10m,filename=app.jfr`.

## P-04 — Localizar no código

- Para cada item de top-N (hot method, alocação, exceção, lock e pausa com causa), gravar os **5 frames de topo** com classe, método e **linha** (o JFR traz o número da linha).
- Cada software ganha `appPackages`, por exemplo `com.empresa,br.com.cliente`, informado no `PATCH /api/v1/software/{name}`.
- A API marca o **primeiro frame da aplicação** (`appFrame`): "seu código: `com.empresa.pedido.PedidoService.calcular:142`". Frames de framework (Spring, Kafka, JDK) aparecem esmaecidos.
- **Modelo**: coluna `stack_frames jsonb` e `app_frame text` nas tabelas de top-N, e `app_packages text` em `software`.

> [!tip] Por que isso importa
> No exemplo real, o top de alocação aponta para `AbstractStringBuilder.<init>`, que é do JDK. O que você precisa saber é **qual classe sua** chamou o `StringBuilder`, e é o `appFrame` que mostra isso.

## P-05 — Novos domínios já presentes nos arquivos

Os arquivos de exemplo trazem eventos que a [[04 Extração JFR]] ainda não usa:

| Sub | Evento (qtd. no exemplo) | O que mostra |
|---|---|---|
| **P-05a** | `jdk.OldObjectSample` (12–37) | **Suspeitos de vazamento**: objetos antigos ainda vivos, com a pilha de quem alocou e a idade |
| **P-05b** | `jdk.ThreadCPULoad` (22–30) | **CPU por thread**: qual thread consome a CPU |
| P-05c | `jdk.SafepointBegin` (34–36) | Tempo da JVM parada fora do GC |
| P-05d | `jdk.NativeMethodSample` (~2.000) | Threads paradas em código nativo (normalmente I/O bloqueante, como socket read) |
| P-05e | `jdk.*Flag` e `jdk.ContainerConfiguration` | Configuração efetiva da JVM e limites do container |

## P-06 — Tendência por software

Como agora todo lote tem software e versão, dá para comparar cada lote com o **anterior do mesmo software**:
- variação das métricas principais (CPU, pausa de GC, taxa de alocação, exceções/s);
- diagnósticos **novos**, **resolvidos** e **persistentes**.

Endpoint: `GET /api/v1/software/{name}/trend`. Responde "a correção da versão 1.4.3 funcionou?".

## P-07 — Contexto do upload

Campos opcionais no upload:
- `environment`: `PROD`, `HML`, `DEV` ou `LOCAL`;
- `description`: até 500 caracteres, por exemplo "lentidão no fechamento às 14h".

Aparecem no histórico e no dashboard. São baratos e ajudam muito quem abre a análise meses depois. Os exemplos vieram de `-Dspring.profiles.active=local` com a IDE, e sem esse contexto alguém poderia confundir com produção.

## P-08 — Exportar relatório

`GET /api/v1/analyses/{batchId}/report.md` gera o diagnóstico (P-01) em Markdown para colar em ticket ou PR. Fica para depois do front-end.
