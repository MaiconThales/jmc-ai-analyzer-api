# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml lombok.config ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/jmc_ai_analyzer_api-*.jar app.jar
COPY docker/api/HealthCheck.java ./hc/
RUN javac -d hc hc/HealthCheck.java

# ---------- runtime ----------
FROM eclipse-temurin:17-jre
ARG APP_UID=1000
ARG APP_GID=1000
# Usuário não-root com UID/GID configuráveis (precisa escrever nos bind mounts de logs e temporários).
RUN (getent group "${APP_GID}" || groupadd -g "${APP_GID}" app) \
    && (getent passwd "${APP_UID}" || useradd -u "${APP_UID}" -g "${APP_GID}" -M -s /usr/sbin/nologin app) \
    && mkdir -p /app/logs /app/tmp && chown -R "${APP_UID}:${APP_GID}" /app
WORKDIR /app
COPY --from=build --chown=${APP_UID}:${APP_GID} /build/app.jar app.jar
COPY --from=build --chown=${APP_UID}:${APP_GID} /build/hc/HealthCheck.class /app/hc/HealthCheck.class
USER ${APP_UID}:${APP_GID}
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    SPRING_PROFILES_ACTIVE=docker \
    APP_TMP_DIR=/app/tmp
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=5 \
    CMD ["java", "-Xmx32m", "-cp", "/app/hc", "HealthCheck", "http://localhost:8080/actuator/health"]
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Duser.language=en -Duser.country=US -Duser.timezone=UTC -jar /app/app.jar"]
