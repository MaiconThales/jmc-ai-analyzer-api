# Plano Técnico do Backend: API do Dashboard JFR/MCP

## 1. Componentes

```text
Angular
   ↓ REST
Spring Boot Dashboard API
    ├── Analysis Orchestrator
    ├── MCP Client / STDIO Bridge
    ├── AI Interpretation Service
    ├── JFR File Catalog
   └── PostgreSQL Repository
            ↓
       PostgreSQL

Spring Boot Dashboard API
   ↓ processo local/container
mcp-server-jmc

Spring Boot Dashboard API
   ↓ HTTP interno ou cliente local
AI provider/model
```

O backend do dashboard será um sistema separado do servidor MCP. O JAR do MCP será executado no mesmo container do backend no primeiro desenho operacional, ou em um container dedicado caso o isolamento seja necessário. A escolha final deve preservar a comunicação STDIO e simplificar o Docker Compose.

## 2. Stack

* Java 21.
* Spring Boot.
* Spring Web.
* Spring Data JPA ou JDBC.
* PostgreSQL.
* Flyway para migrações.
* Bean Validation.
* Cliente MCP Java compatível com o SDK utilizado pelo servidor.
* Cliente HTTP para o provedor de IA ou protocolo compatível com o modelo local.
* Testcontainers para testes de integração com PostgreSQL.
* Maven.

## 3. Organização sugerida

```text
project/
├── src/main/java/io/github/maiconthales/jmc_ai_analyzer_api/
│   ├── JmcAiAnalyzerApiApplication.java
│   ├── api/
│   │   ├── AnalysisController.java
│   │   ├── JfrFileController.java
│   │   └── HealthController.java
│   ├── application/
│   │   ├── AnalysisOrchestrator.java
│   │   ├── AiInterpretationService.java
│   │   └── PromptBuilder.java
│   ├── domain/
│   │   ├── AnalysisSession.java
│   │   ├── AnalysisStage.java
│   │   └── AnalysisStatus.java
│   ├── infrastructure/
│   │   ├── mcp/
│   │   ├── persistence/
│   │   └── filesystem/
│   └── dto/
├── src/main/resources/db/migration/
├── Dockerfile
└── pom.xml
```

Todas as classes devem declarar `package io.github.maiconthales.jmc_ai_analyzer_api...`, conforme o subpacote em que estiverem. `JmcAiAnalyzerApiApplication` deve declarar exatamente `package io.github.maiconthales.jmc_ai_analyzer_api;` e ficar diretamente no pacote raiz, nunca dentro de `api`, `application`, `domain`, `infrastructure` ou `dto`.

## 4. Orquestração MCP

O `AnalysisOrchestrator` deve:

1. Validar o identificador do arquivo.
2. Criar uma sessão `PENDING`.
3. Atualizar a sessão para `RUNNING`.
4. Iniciar o cliente MCP.
5. Executar as cinco tools de análise.
6. Persistir cada resultado individualmente.
7. Persistir erros individuais sem interromper as outras etapas.
8. Calcular o resumo consolidado.
9. Enviar o resumo estruturado para `AiInterpretationService`.
10. Persistir a interpretação ou o erro da IA.
11. Finalizar como `COMPLETED`, `PARTIAL` ou `FAILED`.

O backend deve reutilizar o conteúdo estruturado retornado pelo MCP. Não deve depender do texto formatado da resposta para alimentar os gráficos.

### 4.1. Camada de interpretação por IA

`AiInterpretationService` deve ser uma abstração do provedor. Sua entrada é um `AnalysisResult` consolidado e limitado. Sua saída é um `AiInterpretation` versionado.

Responsabilidades:

* montar o prompt a partir dos dados MCP;
* enviar apenas dados agregados e necessários;
* validar a resposta do modelo;
* registrar provedor, modelo e versão do prompt;
* aplicar timeout;
* retornar erro controlado sem apagar os resultados MCP.

O MVP deve usar a IA como etapa pós-MCP. A IA não deve escolher ferramentas, chamar o MCP novamente ou analisar o arquivo JFR bruto.

## 5. Persistência PostgreSQL

Tabelas iniciais sugeridas:

### `analysis_session`

* `id` UUID;
* `file_name`;
* `file_size_bytes`;
* `file_modified_at`;
* `status`;
* `started_at`;
* `finished_at`;
* `created_at`;
* `error_count`.

### `analysis_stage`

* `id` UUID;
* `analysis_id`;
* `stage_name`;
* `status`;
* `started_at`;
* `finished_at`;
* `error_code`;
* `error_message`.

### `analysis_result`

* `id` UUID;
* `analysis_id`;
* `result_type`;
* `payload` JSONB;
* `created_at`.

### `ai_interpretation`

* `id` UUID;
* `analysis_id` UUID;
* `status`;
* `provider`;
* `model`;
* `prompt_version`;
* `executive_summary`;
* `overall_severity`;
* `payload` JSONB;
* `error_code`;
* `error_message`;
* `created_at`.

O uso de `JSONB` é intencional no MVP: preserva a estrutura dos DTOs MCP sem criar tabelas para cada campo de diagnóstico. Indicadores derivados podem ser materializados posteriormente se consultas analíticas exigirem isso.

## 6. API e contratos

### Arquivos

```text
GET /api/v1/jfr-files
```

Retorna identificadores controlados e metadados, nunca um caminho do sistema que o cliente possa alterar.

### Análise

```text
POST /api/v1/analyses
GET  /api/v1/analyses/{id}
GET  /api/v1/analyses/{id}/status
GET  /api/v1/analyses/{id}/interpretation
```

### Histórico

```text
GET /api/v1/analyses/history?page=0&size=20
```

O contrato de resposta deve conter uma sessão consolidada, uma lista de etapas, resultados disponíveis e erros. O frontend não deve precisar conhecer o protocolo MCP.

## 7. Concorrência e tempo de execução

* Usar executor limitado para as etapas independentes.
* Definir timeout individual para cada chamada MCP.
* Definir timeout global para uma análise.
* Não permitir número ilimitado de análises simultâneas.
* Persistir a sessão antes de iniciar tarefas assíncronas.
* Garantir que uma exceção de uma etapa seja capturada e registrada.
* Executar a interpretação somente depois do consolidado MCP estar disponível.
* Usar timeout separado para a chamada de IA.
* Permitir que a análise MCP termine sem interpretação quando a IA estiver indisponível.
* Na primeira versão, não cancelar uma chamada MCP em execução a partir do frontend; a sessão poderá ser consultada até terminar.

## 8. Docker Compose

Serviços iniciais:

```text
frontend   Angular compilado servido por Nginx
backend    Spring Boot + cliente MCP
postgres   PostgreSQL
ai         modelo local ou adaptador para provedor de IA
```

Volumes:

```text
jfr-data   arquivos JFR disponíveis para análise
pg-data    dados do PostgreSQL
```

Variáveis principais:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
JFR_DATA_DIR
MCP_COMMAND
MCP_ARGUMENTS
ANALYSIS_MAX_CONCURRENT
ANALYSIS_STAGE_TIMEOUT
AI_ENABLED
AI_PROVIDER
AI_BASE_URL
AI_MODEL
AI_TIMEOUT
AI_PROMPT_VERSION
```

O PostgreSQL deve ser acessível somente pela rede interna do Compose. O frontend acessa apenas o backend.

Quando for usado modelo local, o serviço `ai` deve permanecer na rede interna do Compose e não precisa ser exposto ao host. O backend é o único componente autorizado a enviar dados para esse serviço.

## 9. Build de produção

O build final deve ser reproduzível via Docker Compose. O pipeline mínimo será:

1. Compilar o MCP Java.
2. Compilar o backend Java.
3. Compilar o frontend Angular.
4. Construir as imagens do frontend, backend, PostgreSQL e, quando aplicável, do modelo local.
5. Subir PostgreSQL, backend, frontend e serviço de IA.
6. Executar health checks.

O JAR do MCP deve estar disponível para o backend no ambiente de execução, preferencialmente por imagem base ou artefato copiado durante o build. A estratégia de imagem deve evitar dependência de arquivos do host, exceto o volume explícito de JFR.

## 10. Testes

* Testes unitários do orquestrador.
* Testes do adaptador MCP com respostas simuladas.
* Testes de falha parcial.
* Testes da interpretação com cliente de IA simulado.
* Testes de resposta inválida, timeout e indisponibilidade da IA.
* Testes de validação de arquivo.
* Testes de repositórios com Testcontainers PostgreSQL.
* Testes de API com MockMvc ou equivalente.
* Teste de integração do Compose com health checks.

## 11. Decisões e limites

* Sem autenticação nesta fase.
* Sem exposição pública por padrão.
* PostgreSQL armazena histórico, não arquivos JFR.
* Os arquivos JFR ficam em volume configurado.
* A camada de IA é pós-processamento e não orquestra o MCP.
* A IA recebe somente resultados MCP agregados.
* O banco não deve ser acessado pelo frontend.
* O backend deve manter o contrato MCP existente intacto.
