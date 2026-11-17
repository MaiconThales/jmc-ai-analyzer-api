package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisLocation;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import java.util.List;
import java.util.Locale;

/** Construção de itens de diagnóstico e formatação de números para as evidências. */
public final class Diagnoses {

	public static final String APP_PREFIX = "APP:";
	public static final String JMC_PREFIX = "JMC:";

	private Diagnoses() {
	}

	/** Item de uma regra própria, com o score padrão da prioridade. */
	public static DiagnosisItem app(DiagnosisRule rule, Priority priority, DiagnosisCategory category,
			DiagnosisProperties properties, String title, String evidence, DiagnosisLocation location, String action,
			String domainRef) {
		return item(priority, category, APP_PREFIX + rule.id(), defaultScore(priority, properties), title, evidence,
				location, action, domainRef);
	}

	public static DiagnosisItem item(Priority priority, DiagnosisCategory category, String source, int score,
			String title, String evidence, DiagnosisLocation location, String action, String domainRef) {
		DiagnosisItem item = new DiagnosisItem();
		item.setPriority(priority);
		item.setCategory(category);
		item.setSource(source);
		item.setScore(Math.max(0, Math.min(100, score)));
		item.setTitle(title);
		item.setEvidence(evidence);
		item.setLocation(location);
		item.setAction(action);
		item.setDomainRef(domainRef);
		return item;
	}

	public static int defaultScore(Priority priority, DiagnosisProperties properties) {
		return switch (priority) {
			case ALTA -> properties.priorityScore().alta();
			case MEDIA -> properties.priorityScore().media();
			case BAIXA -> properties.priorityScore().baixa();
		};
	}

	public static DiagnosisLocation frames(List<StackFrame> frames) {
		return frames == null || frames.isEmpty() ? null : new DiagnosisLocation(null, null, frames);
	}

	public static String number(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	public static String bytes(double bytes) {
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		int unit = 0;
		double value = bytes;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
	}
}
