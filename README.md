# JMC AI Analyzer API

API que recebe gravações **JFR** (Java Flight Recorder / Java Mission Control), extrai as métricas que ajudam a diagnosticar problemas e devolve um **diagnóstico priorizado em português**: o que arrumar, onde está no código e o que fazer.

- Especificação, plano e tarefas: [`.spec/0.1.0/`](.spec/0.1.0/)
- Documentação de produto (Obsidian): [`jmc-ai-analyzer-api/v0.1.0/`](jmc-ai-analyzer-api/v0.1.0/)

## Stack

Java 17 · Spring Boot 4.1.2 · PostgreSQL 17 · Flyway · JMC core 9.1.2 · SeaweedFS (S3) · Docker Compose.

## Subir com Docker

Todos os dados ficam em `DATA_DIR` (padrão `./docker-data`) na máquina real. Não há volumes nomeados: `docker compose down -v` não apaga nada. O serviço `data-init` cria as pastas com o dono certo a cada `up`, e o `blob` gera a própria configuração S3 a partir do `.env`: não há script para rodar antes.

```bash
cp .env.example .env        # troque os valores "change-me" (chaves do blob: letras, números, - e _)
docker compose up -d --build
```

- Front-end: http://localhost:4200 (serviço `frontend`, construído a partir de `../jmc-ai-analyzer-web`; mude com `FRONTEND_CONTEXT`)
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Saúde: http://localhost:8080/actuator/health

Para expor o banco e o S3 em `127.0.0.1` durante a depuração:

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d
```

### Teste de persistência (CA-06)

1. Envie um `.jfr` e espere o lote ficar `COMPLETED`.
2. Rode `docker compose down -v` e depois `docker compose up -d`.
3. O histórico (`GET /api/v1/analyses`) e o dashboard da gravação continuam disponíveis.

## Uso rápido

```bash
curl -F software=billing-service -F environment=PROD -F "description=lentidão às 14h" \
     -F files=@app-1.jfr -F files=@app-2.jfr http://localhost:8080/api/v1/analyses
```

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/v1/analyses` | Upload de 1..N `.jfr` (`202`) |
| `GET` | `/api/v1/analyses?software=` | Histórico paginado |
| `GET` | `/api/v1/analyses/{batchId}` | Status do lote |
| `GET` | `/api/v1/analyses/{batchId}/dashboard?topN=10` | Dashboard consolidado |
| `GET` | `/api/v1/recordings/{recordingId}/dashboard?topN=10` | Dashboard da gravação |
| `GET` / `PATCH` | `/api/v1/software` · `/api/v1/software/{name}` | Retenção e pacotes da aplicação |
| `GET` | `/api/v1/config` | Limites de upload, top-N e retenção (usado pelo front) |

Defina os pacotes do seu código (`PATCH /api/v1/software/{name}` com `{"appPackages":["com.empresa"]}`) para que o dashboard aponte o primeiro frame da aplicação (`appFrame`) em cada problema.

### Como gravar um bom `.jfr`

```bash
java -XX:StartFlightRecording=settings=profile,duration=10m,filename=app.jfr,jdk.JavaExceptionThrow#enabled=true ...
```

O bloco `coverage` do dashboard indica o que faltou na gravação e sugere o comando.

## Desenvolvimento

Requisitos: JDK 17+, Maven 3.9+, Docker (os testes usam Testcontainers com PostgreSQL e SeaweedFS).

```bash
mvn clean verify
```

O build falha com cobertura abaixo de 90% (linhas e branches, JaCoCo).

Análise no SonarQube (sem CI na v0.1.0):

```bash
mvn clean verify sonar:sonar -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.token=$SONAR_TOKEN
```

## Estrutura

```text
io.github.maiconthales.jmc_ai_analyzer_api
├── system
│   ├── config · properties          configuração Spring e @ConfigurationProperties
│   ├── controllers · dto · mapper   API HTTP, DTOs/records e conversões
│   ├── services                     somente *Service
│   ├── components                   @Component (validação, fila, retenção, extração, regras de diagnóstico)
│   ├── exceptions · handlers        exceções de negócio e ProblemDetail
│   ├── utils                        utilitários estáticos (leitura de eventos JFR, extratores)
│   └── entity · enums · repository  persistência
├── mcp      WIP
└── ia       WIP
```

Detalhes e regras de organização: `.spec/0.1.0/plan.md` §2.
