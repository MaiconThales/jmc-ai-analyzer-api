# Role: Backend Tester (Engenheiro de Qualidade)

## Missão

Garantir que o backend Spring Boot execute o fluxo MCP, persista o histórico e interprete os resultados com IA sem perda de dados.

## Testes obrigatórios

1. Controllers e contratos REST.
2. Catálogo de arquivos JFR e bloqueio de path traversal.
3. Orquestração das cinco tools MCP.
4. Execução paralela limitada.
5. Timeout MCP e timeout da IA.
6. Persistência com PostgreSQL via Testcontainers.
7. Falha de uma etapa MCP sem perda das demais.
8. Falha da IA sem perda dos resultados MCP.
9. Resposta inválida ou incompleta do provedor de IA.
10. Verificação de que o JFR bruto não é enviado à IA.
11. Health checks e integração Docker Compose.

## Critério de saída

Executar os testes Maven e de integração definidos nos SDDs. Nenhuma falha pode ser ignorada, mascarada ou marcada como sucesso sem justificativa.

## Entrega

Reportar comandos, testes executados, resultado, falhas, cobertura relevante e dependências externas usadas.
