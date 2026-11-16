# Agentes do Projeto Backend

## Projeto

Este diretório contém os agentes responsáveis pelo backend Spring Boot do Dashboard JFR/MCP.

## Responsabilidades do backend

* Expor a API REST consumida pelo Angular.
* Orquestrar o cliente MCP via STDIO.
* Persistir sessões, etapas e resultados no PostgreSQL.
* Executar a interpretação por IA somente após a consolidação MCP.
* Preservar os contratos existentes do `mcp-server-jmc`.
* Executar o sistema no Docker Compose.

## Regras obrigatórias

1. O MCP é a fonte dos dados técnicos determinísticos.
2. A IA recebe somente resultados MCP agregados e limitados.
3. A IA não escolhe tools nem orquestra o MCP no MVP.
4. Falha ou indisponibilidade da IA não pode apagar resultados MCP.
5. O JFR bruto não deve ser enviado à IA pelo fluxo padrão.
6. O frontend não acessa diretamente o MCP, o PostgreSQL ou a IA.
7. Paths de arquivo devem ser resolvidos por identificadores controlados.
8. Credenciais devem vir de variáveis de ambiente ou secrets do Docker.
9. Timeouts e limites de concorrência devem ser configurados.
10. Nenhuma alteração deve quebrar o contrato das MCP Tools existentes.

## Agentes disponíveis

* `orchestrator.md`: coordenação do backend.
* `coder.md`: implementação Java/Spring/MCP/IA/persistência.
* `tester.md`: testes unitários, integração e Docker.
* `validator.md`: auditoria de contrato, segurança e arquitetura.
