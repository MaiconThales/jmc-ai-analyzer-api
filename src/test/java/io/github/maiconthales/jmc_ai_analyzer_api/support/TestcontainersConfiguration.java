package io.github.maiconthales.jmc_ai_analyzer_api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Containers compartilhados pelos testes de integração (PostgreSQL e SeaweedFS). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return Containers.POSTGRES;
	}

	@Bean
	GenericContainer<?> seaweedContainer() {
		return Containers.SEAWEED;
	}

	@Bean
	DynamicPropertyRegistrar blobProperties(GenericContainer<?> seaweedContainer) {
		return registry -> {
			registry.add("app.blob.endpoint", Containers::seaweedEndpoint);
			registry.add("app.blob.access-key", () -> Containers.ACCESS_KEY);
			registry.add("app.blob.secret-key", () -> Containers.SECRET_KEY);
		};
	}
}
