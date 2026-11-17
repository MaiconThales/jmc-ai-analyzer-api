package io.github.maiconthales.jmc_ai_analyzer_api.support;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Containers únicos por JVM de teste (padrão singleton), reaproveitados entre classes. */
public final class Containers {

	public static final String SEAWEED_IMAGE = "chrislusf/seaweedfs:4.52";
	public static final String ACCESS_KEY = "test-access";
	public static final String SECRET_KEY = "test-secret";

	public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

	@SuppressWarnings("resource")
	public static final GenericContainer<?> SEAWEED = new GenericContainer<>(SEAWEED_IMAGE)
			.withCopyFileToContainer(MountableFile.forClasspathResource("seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
			.withCommand("server", "-s3", "-dir=/data", "-s3.config=/etc/seaweedfs/s3.json", "-ip.bind=0.0.0.0")
			.withExposedPorts(8333)
			.waitingFor(Wait.forHttp("/healthz").forPort(8333).withStartupTimeout(Duration.ofMinutes(2)));

	static {
		POSTGRES.start();
		SEAWEED.start();
	}

	private Containers() {
	}

	public static String seaweedEndpoint() {
		return "http://" + SEAWEED.getHost() + ":" + SEAWEED.getMappedPort(8333);
	}
}
