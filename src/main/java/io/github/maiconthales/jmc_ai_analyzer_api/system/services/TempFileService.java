package io.github.maiconthales.jmc_ai_analyzer_api.system.services;

import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.StorageProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Arquivos temporários de upload e processamento em {@code app.storage.tmp-dir}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempFileService {

	private static final String PREFIX = "jfr-";

	private final StorageProperties properties;
	private final Clock clock;

	public Path create(String suffix) {
		try {
			Files.createDirectories(properties.tmpDir());
			return Files.createTempFile(properties.tmpDir(), PREFIX, suffix);
		} catch (IOException e) {
			throw new UncheckedIOException("Não foi possível criar arquivo temporário", e);
		}
	}

	public void deleteQuietly(Path file) {
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			log.warn("Não foi possível apagar o temporário {}", file, e);
		}
	}

	/**
	 * Apaga temporários criados por este serviço e mais antigos que {@code app.storage.tmp-max-age}.
	 *
	 * @return quantidade de arquivos apagados
	 */
	public int cleanupOld() {
		Path dir = properties.tmpDir();
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		Instant limit = Instant.now(clock).minus(properties.tmpMaxAge());
		try (Stream<Path> files = Files.list(dir)) {
			return (int) files.filter(f -> f.getFileName().toString().startsWith(PREFIX))
					.filter(f -> isOlderThan(f, limit))
					.filter(this::delete)
					.count();
		} catch (IOException e) {
			log.warn("Falha ao limpar temporários em {}", dir, e);
			return 0;
		}
	}

	private boolean isOlderThan(Path file, Instant limit) {
		try {
			return Files.getLastModifiedTime(file).toInstant().isBefore(limit);
		} catch (IOException e) {
			return false;
		}
	}

	private boolean delete(Path file) {
		try {
			return Files.deleteIfExists(file);
		} catch (IOException e) {
			log.warn("Não foi possível apagar o temporário {}", file, e);
			return false;
		}
	}
}
