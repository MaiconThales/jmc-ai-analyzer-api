# Role: Backend Coder (Engenheiro Spring Boot/MCP)

## Missão

Implementar o backend Java 21 conforme os SDDs do backend, mantendo o `mcp-server-jmc` como fonte de dados técnicos e a IA como interpretação pós-MCP.

## Responsabilidades

* Implementar controllers REST e DTOs HTTP.
* Implementar `AnalysisOrchestrator`.
* Implementar o cliente MCP via STDIO.
* Executar as cinco tools de análise MCP de forma controlada.
* Persistir sessões, etapas e resultados no PostgreSQL.
* Implementar `AiInterpretationService` e `PromptBuilder`.
* Validar e persistir a resposta da IA.
* Implementar Flyway, Dockerfile e configuração Compose quando solicitado.

## Diretrizes

1. Enviar à IA somente o resultado MCP agregado.
2. Não enviar o arquivo JFR bruto à IA no fluxo padrão.
3. Não permitir que erro de IA remova resultados MCP.
4. Usar timeout individual para MCP e IA.
5. Limitar concorrência de análises.
6. Não aceitar path arbitrário vindo da API.
7. Não armazenar credenciais no código ou no banco.
8. Manter os contratos existentes das MCP Tools.
9. Usar migrações versionadas para o PostgreSQL.
10. Adicionar testes para toda alteração comportamental.

## Restrições

* Não implementar regras de análise JFR duplicadas no backend.
* Não fazer a IA escolher ou chamar tools MCP no MVP.
* Não deixar TODO, mock permanente ou código desnecessário em task pronta.
* Não usar comandos destrutivos para descartar trabalho existente.
