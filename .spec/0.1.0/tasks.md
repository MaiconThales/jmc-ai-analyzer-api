# Tasks do Backend: API do Dashboard JFR/MCP

## Épico 1 — Bootstrap e infraestrutura

- [x] **B1.1:** Criar ou manter a aplicação Spring Boot independente no projeto atual, usando o pacote raiz `io.github.maiconthales.jmc_ai_analyzer_api`.
- [x] **B1.1.1:** Manter `JmcAiAnalyzerApiApplication` diretamente em `io.github.maiconthales.jmc_ai_analyzer_api`, fora dos subpacotes, para preservar o escaneamento automático do Spring Boot.
- [x] **B1.1.2:** Organizar os demais componentes em subpacotes abaixo de `io.github.maiconthales.jmc_ai_analyzer_api`.
- [x] **B1.2:** Configurar Java 21, Maven e dependências web, persistência e validação.
- [x] **B1.3:** Configurar profiles local e Docker.
- [x] **B1.4:** Criar Dockerfile do backend.
- [x] **B1.5:** Criar health check da aplicação.

## Épico 2 — Banco e histórico

- [x] **B2.1:** Configurar PostgreSQL e Flyway.
- [x] **B2.2:** Criar migração de `analysis_session`.
- [x] **B2.3:** Criar migração de `analysis_stage`.
- [x] **B2.4:** Criar migração de `analysis_result` com coluna JSONB.
- [x] **B2.5:** Implementar entidades, repositórios e consulta paginada do histórico.
- [x] **B2.6:** Criar testes de persistência com Testcontainers.
- [x] **B2.7:** Criar migração de `ai_interpretation` com payload JSONB e metadados do modelo.

## Épico 3 — Catálogo de arquivos JFR

- [x] **B3.1:** Implementar diretório configurável de JFR.
- [x] **B3.2:** Listar somente arquivos `.jfr` regulares e legíveis.
- [x] **B3.3:** Gerar identificador controlado para cada arquivo.
- [x] **B3.4:** Implementar `GET /api/v1/jfr-files`.
- [x] **B3.5:** Cobrir validação de path traversal e arquivo inexistente.

## Épico 4 — Cliente e orquestração MCP

- [x] **B4.1:** Implementar inicialização do cliente MCP via STDIO.
- [x] **B4.2:** Implementar adaptador para as cinco tools de análise.
- [x] **B4.3:** Implementar criação e atualização da sessão.
- [x] **B4.4:** Implementar execução paralela limitada das etapas.
- [x] **B4.5:** Implementar timeout individual por chamada MCP e timeout global por análise.
- [x] **B4.6:** Persistir respostas estruturadas bem-sucedidas em `analysis_result` após a execução MCP, preservando JSONB e resultados já válidos.
- [x] **B4.7:** Persistir erros por etapa. (Fora do escopo de B4.6.)
- [x] **B4.8:** Implementar cálculo de `COMPLETED`, `PARTIAL` e `FAILED`. (Fora do escopo de B4.6.)

## Épico 5 — Interpretação pós-MCP

- [x] **B5.1:** Definir contrato interno `AnalysisResult` consolidado, imutável, limitado e sem paths/JFR bruto.
- [x] **B5.2:** Criar `AiInterpretationService` provider-neutral com saída `AiInterpretation` versionada.
- [x] **B5.3:** Criar `PromptBuilder` com versão explícita, determinismo e limite global de tamanho.
- [x] **B5.4:** Implementar cliente para o provedor/modelo escolhido.
- [x] **B5.5:** Enviar somente resultados MCP agregados para a IA.
- [x] **B5.6:** Validar e normalizar a resposta da IA.
- [x] **B5.7:** Persistir interpretação, modelo, provedor e versão do prompt.
- [x] **B5.8:** Implementar timeout, resposta inválida e indisponibilidade da IA.
- [x] **B5.9:** Garantir que falha da IA não invalide os resultados MCP.

## Épico 6 — API do dashboard

- [x] **B6.1:** Implementar `POST /api/v1/analyses`.
- [x] **B6.2:** Implementar `GET /api/v1/analyses/{id}`.
- [x] **B6.3:** Implementar `GET /api/v1/analyses/{id}/status`.
- [x] **B6.4:** Implementar `GET /api/v1/analyses/{id}/interpretation`.
- [x] **B6.5:** Implementar `GET /api/v1/analyses/history`.
- [x] **B6.6:** Definir DTOs HTTP independentes dos DTOs internos do MCP.
- [x] **B6.7:** Padronizar respostas de erro.
- [x] **B6.8:** Configurar CORS somente para o frontend local do Compose.

## Épico 7 — Docker e integração

- [ ] **B7.1:** Criar Docker Compose com frontend, backend, PostgreSQL e serviço de IA quando aplicável. (Pendente: não há imagem Angular compilada versionada/provisionada neste repositório; `FRONTEND_IMAGE` é obrigatório.)
- [x] **B7.2:** Montar volume de arquivos JFR.
- [x] **B7.3:** Configurar volume persistente do PostgreSQL.
- [ ] **B7.4:** Disponibilizar o JAR MCP no container do backend. (Pendente: o JAR MCP versionado não está no repositório; o override `docker-compose.mcp.yml` exige artefato, versão e SHA-256 externos.)
- [x] **B7.5:** Configurar imagem ou conexão do modelo de IA. (Perfil explícito exige imagem/modelo provisionados e habilita `AI_ENABLED=true`.)
- [x] **B7.6:** Configurar health checks e dependências de inicialização.
- [ ] **B7.7:** Validar análise completa dentro do Compose.

## Épico 8 — Testes e qualidade

- [x] **B8.1:** Testar orquestração com cliente MCP simulado.
- [x] **B8.2:** Testar sucesso de todas as etapas.
- [x] **B8.3:** Testar falha parcial.
- [x] **B8.4:** Testar falha total e timeout.
- [x] **B8.5:** Testar interpretação com provedor simulado.
- [x] **B8.6:** Testar resposta inválida, timeout e indisponibilidade da IA.
- [x] **B8.7:** Testar endpoints com MockMvc.
- [ ] **B8.8:** Executar build Maven e testes dentro do container.
