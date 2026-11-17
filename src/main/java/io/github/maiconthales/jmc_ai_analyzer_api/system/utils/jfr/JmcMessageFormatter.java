package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openjdk.jmc.flightrecorder.rules.IResult;
import org.openjdk.jmc.flightrecorder.rules.ResultToolkit;

/**
 * Preenche os placeholders das mensagens do JMC e sanitiza o HTML, mantendo apenas
 * {@code <p>}, {@code <ul>}, {@code <li>}, {@code <b>} e {@code <code>} (sem atributos).
 */
public final class JmcMessageFormatter {

	static final Set<String> ALLOWED_TAGS = Set.of("p", "ul", "li", "b", "code");
	private static final Pattern TAG = Pattern.compile("<\\s*(/?)\\s*([a-zA-Z0-9]+)[^>]*>");
	private static final Pattern BREAK = Pattern.compile("<\\s*br\\s*/?\\s*>", Pattern.CASE_INSENSITIVE);

	private JmcMessageFormatter() {
	}

	public static String format(IResult result, String template) {
		if (template == null || template.isBlank()) {
			return null;
		}
		return sanitize(ResultToolkit.populateMessage(result, template, false));
	}

	public static String sanitize(String html) {
		if (html == null) {
			return null;
		}
		String text = BREAK.matcher(html).replaceAll(" ");
		Matcher matcher = TAG.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String tag = matcher.group(2).toLowerCase(Locale.ROOT);
			String replacement = ALLOWED_TAGS.contains(tag) ? "<" + matcher.group(1) + tag + ">" : "";
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		String result = out.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").strip();
		return result.isEmpty() ? null : result;
	}
}
