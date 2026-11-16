# Especificação do Backend: API do Dashboard JFR/MCP

## 1. Objetivo

Criar uma aplicação Spring Boot independente para servir de adaptador entre o dashboard Angular, o `mcp-server-jmc`, a camada de interpretação por IA e o histórico PostgreSQL.

## 1.1. Convenções do projeto

O código da aplicação deve utilizar o pacote raiz `io.github.maiconthales.jmc_ai_analyzer_api`.

A classe principal da aplicação Spring Boot deve permanecer diretamente nesse pacote raiz, fora dos subpacotes, para que o componente de escaneamento do Spring Boot encontre automaticamente os componentes da aplicação. Os demais componentes devem ser organizados em subpacotes abaixo do pacote raiz, como `api`, `application`, `domain`, `infrastructure` e `dto`.

O backend deve consumir as ferramentas MCP existentes, consolidar seus resultados em uma sessão de análise, enviar o resultado consolidado para a camada de IA e disponibilizar uma API HTTP compreensível para o frontend. A lógica de análise JFR deve permanecer no MCP; o backend não deve duplicar agregadores ou interpretar eventos JFR diretamente no MVP. A IA entra somente após a conclusão das análises MCP.

## 2. Escopo

### 2.1. Incluído

* API REST para listar arquivos JFR.
* API REST para iniciar uma análise.
* Orquestração das ferramentas MCP de análise.
* Execução das análises independentes em paralelo.
* Persistência de sessões e resultados no PostgreSQL.
* Geração de interpretação pós-MCP por serviço de IA.
* Persistência da interpretação, modelo, provedor e versão do prompt.
* Consulta do estado e do resultado de uma análise.
* Histórico de análises.
* Tratamento de falhas parciais.
* Execução local do processo MCP dentro do ambiente Docker.
* Configuração por variáveis de ambiente.

### 2.2. Fora do escopo inicial

* Autenticação e autorização.
* Multi-tenancy.
* Exposição pública na internet.
* Upload remoto arbitrário de arquivos.
* Monitoramento contínuo.
* Reimplementação dos serviços JFR do MCP.
* Uso da IA para decidir quais ferramentas MCP devem ser chamadas.
* Alteração dos contratos existentes das tools MCP.
* Comparação avançada entre análises.

## 3. Requisitos funcionais

### RF-01 — Listar arquivos JFR

O backend deve listar apenas arquivos `.jfr` legíveis dentro do diretório configurado para gravações. A resposta deve conter identificador seguro, nome, tamanho e data de modificação.

### RF-02 — Iniciar análise

O backend deve aceitar a identificação de um arquivo JFR e os parâmetros opcionais das ferramentas, criar uma sessão e retornar um `analysisId`.

### RF-03 — Orquestrar MCP

Para cada sessão, o backend deve executar:

* `analyze_jfr_automated_rules`;
* `get_cpu_hot_methods`;
* `analyze_gc_and_memory`;
* `find_lock_contentions`;
* `get_frequent_exceptions`.

As chamadas podem ser paralelas após a validação do arquivo.

### RF-04 — Persistir progresso

O backend deve persistir o estado global e o estado de cada etapa. Estados mínimos: `PENDING`, `RUNNING`, `COMPLETED`, `PARTIAL`, `FAILED`.

### RF-05 — Tratar falhas parciais

Uma falha em uma tool não deve apagar resultados obtidos pelas outras. A sessão deve terminar como `PARTIAL` quando houver pelo menos um resultado válido e uma ou mais falhas.

### RF-06 — Consultar análise

O backend deve retornar uma representação consolidada contendo resumo, diagnóstico, CPU, GC/memória, locks, exceções, erros e metadados.

### RF-07 — Consultar histórico

O backend deve permitir listar análises anteriores e obter uma análise específica.

### RF-08 — Health check

O backend deve disponibilizar endpoint de saúde que indique disponibilidade da aplicação e conectividade com o PostgreSQL. A disponibilidade do MCP pode ser informada como dependência operacional.

### RF-09 — Interpretar resultados pós-MCP

Depois que as etapas MCP terminarem, o backend deve enviar somente os resultados estruturados e agregados para a camada de IA. O arquivo JFR bruto não deve ser enviado à IA pelo fluxo padrão.

A interpretação deve produzir, quando possível, resumo executivo, severidade geral, principais achados, hipóteses ou possíveis causas, recomendações, modelo e provedor utilizados e versão do prompt.

### RF-10 — Isolar falhas da IA

Uma falha, timeout ou indisponibilidade da IA não deve invalidar os resultados MCP. A sessão deve manter os dados técnicos e registrar a interpretação como `FAILED` ou `UNAVAILABLE`.

### RF-11 — Configurar provedor de IA

O provedor, modelo, URL, timeout e política de ativação da IA devem ser configuráveis por ambiente. Credenciais não podem ser armazenadas no código ou no banco.

## 4. API HTTP inicial

```text
GET  /api/v1/health
GET  /api/v1/jfr-files
POST /api/v1/analyses
GET  /api/v1/analyses/{analysisId}
GET  /api/v1/analyses/{analysisId}/status
GET  /api/v1/analyses/history
GET  /api/v1/analyses/{analysisId}/interpretation
```

### POST `/api/v1/analyses`

Exemplo de entrada:

```json
{
  "fileId": "app-2026-11-16.jfr",
  "minScore": 50.0,
  "topN": 10,
  "topClasses": 10,
  "minDurationMs": 50
}
```

Exemplo de resposta:

```json
{
  "analysisId": "uuid",
  "status": "PENDING"
}
```

## 5. Requisitos não funcionais

* Java 21 e Spring Boot.
* PostgreSQL como banco de histórico.
* Comunicação MCP via cliente oficial ou adaptador compatível com STDIO.
* Nenhum log da aplicação deve ser enviado ao stdout do processo MCP.
* Caminhos recebidos pela API devem ser convertidos a identificadores controlados; não aceitar path arbitrário do cliente.
* O diretório de JFR deve ser configurável e montado como volume Docker.
* Timeouts devem existir por chamada MCP e por sessão completa.
* O backend deve limitar o número de análises concorrentes.
* O backend não deve retornar listas brutas ilimitadas.
* Falhas devem possuir código e mensagem segura para o frontend.
* A chamada de IA deve possuir timeout próprio e limite de concorrência.
* A resposta da IA deve ser validada antes de ser persistida e exposta ao frontend.

## 6. Critérios de aceite

1. O backend lista somente arquivos JFR do diretório configurado.
2. Uma análise cria uma sessão persistida antes da execução das tools.
3. As cinco tools de análise são chamadas sem o frontend conhecer seus nomes.
4. O resultado estruturado de cada tool é persistido.
5. A análise suporta conclusão total, conclusão parcial e falha total.
6. O histórico sobrevive à reinicialização do backend.
7. A interpretação é iniciada somente após o consolidado MCP estar disponível.
8. A indisponibilidade da IA não remove nem invalida os resultados MCP.
9. A interpretação persistida pode ser consultada pelo frontend.
10. O backend funciona no Docker Compose com PostgreSQL, MCP e serviço de IA configurado.
11. Nenhum caminho arbitrário ou comando informado pelo cliente é executado diretamente.
