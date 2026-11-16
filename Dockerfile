FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests \
    && cp target/jmc-ai-analyzer-api-*.jar /workspace/target/jmc-ai-analyzer-api.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/mcp /data/jfr \
    && useradd --system --uid 10001 --no-create-home appuser \
    && chown -R appuser:appuser /app /opt/mcp /data/jfr
COPY --from=build /workspace/target/jmc-ai-analyzer-api.jar app.jar
COPY docker/backend-entrypoint.sh /usr/local/bin/backend-entrypoint.sh
RUN chmod 0555 /usr/local/bin/backend-entrypoint.sh

ENV SPRING_PROFILES_ACTIVE=docker
ENV MCP_JAR_PATH=/opt/mcp/mcp-server-jmc.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/api/v1/health || exit 1

USER 10001
ENTRYPOINT ["/usr/local/bin/backend-entrypoint.sh"]
