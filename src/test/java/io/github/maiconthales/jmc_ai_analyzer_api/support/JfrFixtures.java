package io.github.maiconthales.jmc_ai_analyzer_api.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Acesso às gravações reais de {@code src/test/resources/jfr} e geração de arquivos inválidos. */
public final class JfrFixtures {

	public static final List<String> EXAMPLES = List.of(
			"jmc-29300-1794599415713.jfr",
			"jmc-29300-1794599647835.jfr",
			"jmc-29300-1794599808505.jfr");

	private JfrFixtures() {
	}

	public static Path example(int index) {
		return Path.of("src/test/resources/jfr", EXAMPLES.get(index));
	}

	public static byte[] bytes(Path path) {
		try {
			return Files.readAllBytes(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Cópia truncada (cabeçalho válido, conteúdo cortado) de um exemplo. */
	public static Path truncated(Path dir) {
		byte[] original = bytes(example(0));
		byte[] cut = new byte[original.length / 3];
		System.arraycopy(original, 0, cut, 0, cut.length);
		return write(dir.resolve("truncated.jfr"), cut);
	}

	/** Arquivo com extensão {@code .jfr} e magic number inválido. */
	public static Path badMagic(Path dir) {
		return write(dir.resolve("bad-magic.jfr"), "isto nao e um jfr".getBytes());
	}

	public static Path write(Path path, byte[] content) {
		try {
			Files.write(path, content);
			return path;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
