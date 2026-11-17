package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitura de flags da JVM considerando a <b>última ocorrência</b> (é a que a HotSpot aplica)
 * e extração da versão do jar dos argumentos da aplicação.
 */
public final class JvmArgsParser {

	private static final Pattern JAR_VERSION = Pattern.compile(
			"(?:^|[\\s/\\\\])[A-Za-z0-9._-]+?-(\\d+(?:\\.\\d+)+(?:[-.][A-Za-z0-9]+)*)\\.jar(?:\\s|$)");
	private static final Pattern MEMORY = Pattern.compile("(\\d+)([kKmMgGtT]?)");

	private JvmArgsParser() {
	}

	/** Valor de {@code -XX:Nome=valor}, se presente. */
	public static Optional<String> xxValue(String jvmArgs, String name) {
		return lastMatch(jvmArgs, Pattern.compile("-XX:" + Pattern.quote(name) + "=(\\S+)"));
	}

	/** Quantas vezes {@code -XX:Nome=} aparece. */
	public static int xxCount(String jvmArgs, String name) {
		if (jvmArgs == null) {
			return 0;
		}
		Matcher matcher = Pattern.compile("-XX:" + Pattern.quote(name) + "=").matcher(jvmArgs);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	/** Valor em bytes de {@code -Xms}/{@code -Xmx} (prefixo informado), se presente. */
	public static Optional<Long> memoryFlag(String jvmArgs, String prefix) {
		return lastMatch(jvmArgs, Pattern.compile("(?:^|\\s)" + Pattern.quote(prefix) + "(\\d+[kKmMgGtT]?)(?:\\s|$)"))
				.map(JvmArgsParser::toBytes);
	}

	/** Versão do jar em {@code javaArgs}, por exemplo {@code app-1.4.2.jar} → {@code 1.4.2}. */
	public static Optional<String> jarVersion(String javaArgs) {
		if (javaArgs == null) {
			return Optional.empty();
		}
		Matcher matcher = JAR_VERSION.matcher(javaArgs);
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	static long toBytes(String value) {
		Matcher matcher = MEMORY.matcher(value);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Tamanho de memória inválido: " + value);
		}
		long number = Long.parseLong(matcher.group(1));
		return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
			case "k" -> number << 10;
			case "m" -> number << 20;
			case "g" -> number << 30;
			case "t" -> number << 40;
			default -> number;
		};
	}

	private static Optional<String> lastMatch(String text, Pattern pattern) {
		if (text == null) {
			return Optional.empty();
		}
		Matcher matcher = pattern.matcher(text);
		String last = null;
		while (matcher.find()) {
			last = matcher.group(1);
		}
		return Optional.ofNullable(last);
	}
}
