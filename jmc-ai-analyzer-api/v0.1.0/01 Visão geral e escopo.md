---
tags: [jmc-ai-analyzer, v0-1-0, escopo]
versao: 0.1.0
status: rascunho
---

# Visão geral e escopo

Voltar: [[00 Índice]]

## Objetivo

Receber via upload **um ou vários arquivos `.jfr`** gerados pelo Java Flight Recorder (abertos normalmente no Java Mission Control) e, a partir deles:

1. extrair **tudo o que for possível para determinar problemas no software** (ver [[04 Extração JFR]]);
2. persistir o resultado no PostgreSQL para **histórico e análise futura** (ver [[05 Modelo de dados]]);
3. expor os dados prontos para montar um **dashboard**, por arquivo e consolidado (ver [[06 API]]).

> [!important] A leitura do JFR acontece no próprio back-end
> A versão anterior (removida no commit `a94d0a0`) delegava a análise a um `mcp-server-jmc` externo. **Isso não vale mais.** Na v0.1.0, o pacote `system` lê o JFR direto com as bibliotecas core do JMC. Ver [[08 Decisões e questões#D-01 — Parser JFR no back-end]].

## Escopo

> [!success] Entra na v0.1.0
> - Upload multipart de 1..N arquivos `.jfr` numa só requisição (um **lote**).
> - Arquivo original guardado num container de **blob storage** (SeaweedFS, API S3), com SHA-256, para reprocessar no futuro. Ver [[09 Armazenamento blob]].
> - Todos os dados dos containers mapeados para pastas da máquina real. Ver [[07 Infraestrutura e qualidade#Persistência no host]].
> - Processamento assíncrono, com status por arquivo e por lote.
> - Extração de métricas por domínio e dos achados das regras automáticas do JMC.
> - Persistência de **agregados + séries temporais reduzidas + top-N**, sem eventos brutos.
> - Dashboard **por arquivo** e **consolidado por lote**.
> - Histórico paginado.
> - **Diagnóstico com prioridade** (o que arrumar primeiro), **cobertura da gravação** e **localização no código**. Ver [[10 Propostas de diagnóstico]].
> - Suspeitos de vazamento de memória e CPU por thread.
> - Contexto do upload: software, versão, ambiente e descrição.
> - Health check.
> - Docker Compose com `postgres`, `blob` e `api`, já com espaço para `mcp` e `frontend`.

> [!failure] Fica fora da v0.1.0
> - Implementação dos pacotes `ia` e `mcp` (ficam em **WIP**).
> - Front-end.
> - Autenticação e autorização.
> - Persistência de eventos JFR brutos.
> - Endpoint de reprocessamento (o armazenamento já fica pronto para ele).
> - Exclusão de análises e política de retenção.
> - Comparação entre lotes e tendência por software ([[10 Propostas de diagnóstico#P-06 — Tendência por software|P-06]]), prevista para a v0.2.0.
> - Exportar relatório em Markdown ([[10 Propostas de diagnóstico#P-08 — Exportar relatório|P-08]]), prevista para a v0.2.0.
> - Domínios P-05c, P-05d e P-05e (safepoints, métodos nativos, flags e container).

## Glossário

| Termo | Significado |
|---|---|
| **Lote** (*batch*) | Conjunto de arquivos enviados num mesmo upload. Tem um `batchId`. |
| **Gravação** (*recording*) | Um arquivo `.jfr` dentro de um lote. Tem um `recordingId`. |
| **Domínio** | Grupo de métricas: CPU, heap, GC, threads, locks, hot methods, alocação, exceções, I/O e achados. |
| **Top-N** | Os N itens mais relevantes de um domínio, por exemplo os 10 métodos mais amostrados. |
| **Achado** (*finding*) | Resultado de uma regra automática do JMC, com severidade e sugestão. |
| **Consolidado** | Visão agregada de todas as gravações `COMPLETED` de um lote. |
