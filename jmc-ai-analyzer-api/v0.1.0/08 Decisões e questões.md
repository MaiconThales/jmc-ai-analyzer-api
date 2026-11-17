---
tags: [jmc-ai-analyzer, v0-1-0, decisoes]
versao: 0.1.0
status: rascunho
---

# Decisões e questões

Voltar: [[00 Índice]]

## Decisões confirmadas (2026-11-17)

### D-00 — Entradas do Arquivo bruto

> [!success] Decisão
> Pacotes `system`, `mcp` e `ia`; Java 17; Spring Boot; PostgreSQL; Docker; Lombok; regras Sonar; cobertura de 90%; `ia` e `mcp` em WIP. Fonte: [[Arquivo bruto]].

### D-01 — Parser JFR no back-end

> [!success] Decisão
> A leitura do JFR é feita no `system`, com as bibliotecas core do JMC 9.1.2 (bytecode Java 17, conferido).
> **Por quê**: além de ler eventos, como o `jdk.jfr.consumer.RecordingFile` do JDK, elas trazem o **motor de regras do JMC**, que gera achados prontos ("tudo que ajuda a determinar problemas").
> **Alternativa descartada**: usar o `mcp-server-jmc` como antes. O MCP volta no futuro, no pacote `mcp`.

### D-02 — Granularidade da persistência

> [!success] Decisão
> **Agregados + séries reduzidas + top-N.** Sem eventos brutos.
> **Por quê**: banco enxuto e dashboard rápido. O detalhe completo continua disponível no `.jfr` guardado (D-03).

### D-03 — Arquivo .jfr em blob storage (SeaweedFS)

> [!success] Decisão (revisada em 2026-11-17)
> O original fica num **container de blob** dedicado, o SeaweedFS, acessado pela API S3, na chave `recordings/<sha256>.jfr`. O banco guarda bucket, chave e hash.
> **Por quê**: permite reprocessar quando o parser evoluir, sem inchar o PostgreSQL. Também deixa a API sem estado e permite trocar por qualquer servidor S3 só com configuração.
> **Alternativas descartadas**: volume montado direto na API (versão anterior desta decisão); MinIO, porque a edição comunitária parou de publicar imagens; Garage e Azurite.
> Detalhes em [[09 Armazenamento blob]].

### D-04 — Consolidado calculado na consulta

> [!success] Decisão
> Cada arquivo gera a sua análise, e o lote ganha uma visão consolidada.
> O consolidado é **calculado na consulta**, sem tabela própria, para nunca ficar desatualizado. Ver [[04 Extração JFR#Consolidação do lote]].

### D-05 — DTOs em controllers.dto

> [!failure] Substituída pela [[#D-18 — Organização de pacotes|D-18]]
> Os DTOs ficavam em `controllers.dto`; agora ficam em `dto`, no mesmo nível de `controllers`.

### D-06 — Spring Boot 4.1.2

> [!success] Decisão
> Mantida a versão do `pom.xml`. Impactos: starters novos (ver [[02 Arquitetura#Starters do Spring Boot 4.1.2]]), Jackson 3 e Hibernate 7.

### D-07 — Processamento assíncrono

> [!success] Decisão
> O upload responde `202` e o processamento acontece em segundo plano.
> **Por quê**: um `.jfr` grande pode levar minutos, e vários arquivos em série estourariam o timeout HTTP.

### D-08 — Local da documentação

> [!success] Decisão
> Primeiro, as notas Obsidian nesta pasta. Depois da aprovação, o SDD vai para `<raiz do repositório>/.spec/0.1.0/` (`spec.md`, `plan.md`, `tasks.md`).
> O [[Arquivo bruto]] diz `~/.spec/0.1.0/`. Como o histórico do git mostra que a pasta ficava na raiz do projeto, a leitura adotada é a raiz do repositório.

### D-09 — Todo dado do Docker mapeado no host

> [!success] Decisão
> Todos os serviços usam *bind mount* para `${DATA_DIR}` (padrão `./docker-data`), sem *named volumes*.
> **Por quê**: reiniciar, recriar ou rodar `docker compose down -v` não pode apagar nada, e os dados ficam visíveis e fáceis de copiar para backup na máquina real.
> Detalhes em [[07 Infraestrutura e qualidade#Persistência no host]].

### D-10 — SonarQube local, sem CI (Q1 e Q5)

> [!success] Decisão
> Existe um SonarQube. Na v0.1.0 a análise roda localmente com `mvn verify sonar:sonar` e `sonar.qualitygate.wait=true`. Pipeline de CI fica para depois.
> Detalhes em [[07 Infraestrutura e qualidade#Sonar]].

### D-11 — Limites de upload

> [!success] Decisão (Q2, confirmada em 2026-11-17)
> **20 arquivos por lote, 128 MB por arquivo, 1 GB por requisição**, `max-concurrent = 2` e **3 GB** de memória para a `api`.
> **Medido em 2026-11-17**: gravações densas de ~240 MB usam 1–1,5 GB de heap (3–5×), não 20×. Escolhido: 128 MB por arquivo e 3 GB para a API.

### D-12 — Software e retenção por dias (Q3)

> [!success] Decisão
> Todo lote pertence a um **software** (campo obrigatório no upload). A versão é só um atributo e pode mudar. A retenção é **em dias, por software** (padrão 90), aplicada por um job diário.
> Detalhes em [[03 Requisitos#RF-11 — Software e retenção]].

### D-13 — Swagger na v0.1.0 (Q4)

> [!success] Decisão
> `springdoc-openapi` 3.x com Swagger UI. Ver [[03 Requisitos#RF-12 — Documentação da API (Swagger)]].

### D-14 — Fixtures reais (Q6)

> [!success] Decisão
> Os arquivos de `examples files/` viram fixtures de teste. Domínios ausentes neles (exceções lançadas e I/O de arquivo) são cobertos por gravações geradas no teste.
> Ver [[07 Infraestrutura e qualidade#Testes]].

### D-15 — Severidades do JMC

> [!success] Decisão (vem do spike)
> O JMC 9.1.2 só tem `NA`, `OK`, `INFO`, `WARNING` e `IGNORE`, e o `CRITICAL` foi removido do modelo. A prioridade para o usuário vem da proposta [[10 Propostas de diagnóstico#P-01 — Diagnóstico unificado com prioridade]].

### D-16 — Propostas de diagnóstico aprovadas

> [!success] Decisão
> Entram na v0.1.0 as propostas recomendadas da [[10 Propostas de diagnóstico]]:
> - P-01: diagnóstico com prioridade;
> - P-02: catálogo de regras em português;
> - P-03: cobertura da gravação;
> - P-04: localizar no código;
> - P-05a: suspeitos de vazamento;
> - P-05b: CPU por thread;
> - P-07: contexto do upload.
>
> Ficam para a v0.2.0: P-06 (tendência por software), P-08 (exportar relatório) e P-05c a P-05e.
> Requisitos: [[03 Requisitos#RF-13 — Diagnóstico com prioridade|RF-13]], [[03 Requisitos#RF-14 — Cobertura da gravação|RF-14]] e [[03 Requisitos#RF-15 — Localizar no código|RF-15]].

### D-17 — Localização do diagnóstico em jsonb

> [!success] Decisão (tomada ao gerar o SDD)
> `diagnosis_item.location` é `jsonb` (`{thread?, target?, stackFrames?}`), e não `text`, porque o `appFrame` é calculado na consulta a partir da pilha. `recording_summary` também ganha `suggested_command`, que vem da cobertura.

### D-18 — Organização de pacotes

> [!success] Decisão (2026-11-17)
> - `properties`: só `*Properties`; `config`: só `@Configuration`.
> - `dto`, `exceptions`, `components` e `mapper` no mesmo nível de `controllers`.
> - Exceções em `exceptions`; o `ApiExceptionHandler` continua em `handlers` (é um handler).
> - `services`: só classes `*Service`. Por isso `BatchCreationService` virou `BatchCreationService`, `AnalysisProcessingService` virou `AnalysisProcessingService` e `AnalysisPersistenceService` virou `AnalysisPersistenceService`.
> - `@Component` em `components` (inclusive as 13 regras de diagnóstico); `*Mapper` em `mapper`.
> - Records internos em subpacotes de `dto` (`jfr`, `diagnosis`, `dashboard`, `processing`); utilitários estáticos em `utils`.
> - Subpacotes por assunto permitidos.
> Árvore completa em [[02 Arquitetura#Estrutura de pacotes]].

## Questões respondidas

- [x] **Q1 — Sonar** → existe SonarQube → [[#D-10 — SonarQube local, sem CI (Q1 e Q5)|D-10]]
- [x] **Q2 — Limites** → proposta para validar → [[#D-11 — Limites de upload|D-11]]
- [x] **Q3 — Retenção** → por software, em dias → [[#D-12 — Software e retenção por dias (Q3)|D-12]]
- [x] **Q4 — Swagger** → sim → [[#D-13 — Swagger na v0.1.0 (Q4)|D-13]]
- [x] **Q5 — CI** → não agora → [[#D-10 — SonarQube local, sem CI (Q1 e Q5)|D-10]]
- [x] **Q6 — Fixtures** → `examples files/` → [[#D-14 — Fixtures reais (Q6)|D-14]]

## Pendências

- [x] Medir os limites de [[#D-11 — Limites de upload|D-11]] com um `.jfr` grande → 128 MB e 3 GB.
- [x] Decidir quais propostas de [[10 Propostas de diagnóstico]] entram na v0.1.0 → [[#D-16 — Propostas de diagnóstico aprovadas|D-16]].

## Próximo passo

> [!success] SDD gerado em 2026-11-17 em `.spec/0.1.0/` (`spec.md`, `plan.md`, `tasks.md`).
> A medição dos limites (D-11) virou a tarefa **T-02.3**.

Mapeamento usado:

| Arquivo | Conteúdo principal |
|---|---|
| `.spec/0.1.0/spec.md` | [[01 Visão geral e escopo]], [[03 Requisitos]], contratos de [[06 API]] e domínios de [[04 Extração JFR]] |
| `.spec/0.1.0/plan.md` | [[02 Arquitetura]], [[05 Modelo de dados]], [[07 Infraestrutura e qualidade]], [[09 Armazenamento blob]] e decisões desta nota |
| `.spec/0.1.0/tasks.md` | épicos: Bootstrap → Spike SeaweedFS + springdoc → Blob storage → Software e upload → Extratores → Persistência → Diagnóstico, cobertura e localização → Dashboards → Retenção → Swagger → Docker → Qualidade |
