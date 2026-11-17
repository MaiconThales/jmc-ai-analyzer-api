package io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.JmcRulesResult;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.NotApplicableRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.FindingSeverity;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.AnalysisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JmcMessageFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.util.IPreferenceValueProvider;
import org.openjdk.jmc.flightrecorder.rules.IRecordingSetting;
import org.openjdk.jmc.flightrecorder.rules.IResult;
import org.openjdk.jmc.flightrecorder.rules.IRule;
import org.openjdk.jmc.flightrecorder.rules.RuleRegistry;
import org.openjdk.jmc.flightrecorder.rules.Severity;
import org.openjdk.jmc.flightrecorder.rules.TypedResult;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit;
import org.springframework.stereotype.Component;

/** Executa todas as regras automáticas do JMC sobre uma gravação. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JmcRuleRunner {

	private final AnalysisProperties properties;

	public static Collection<IRule> rules() {
		return RuleRegistry.getRules();
	}

	public JmcRulesResult run(IItemCollection items) {
		Map<IRule, Future<IResult>> futures = RulesToolkit.evaluateParallel(rules(), items,
				IPreferenceValueProvider.DEFAULT_VALUES, properties.ruleThreads());
		List<RuleFinding> findings = new ArrayList<>();
		List<NotApplicableRule> notApplicable = new ArrayList<>();
		for (Map.Entry<IRule, Future<IResult>> entry : futures.entrySet()) {
			IResult result = await(entry.getKey(), entry.getValue());
			if (result != null) {
				classify(entry.getKey(), result, findings, notApplicable);
			}
		}
		findings.sort(Comparator.comparing(RuleFinding::getRuleId));
		notApplicable.sort(Comparator.comparing(NotApplicableRule::ruleId));
		return new JmcRulesResult(findings, notApplicable);
	}

	static void classify(IRule rule, IResult result, List<RuleFinding> findings, List<NotApplicableRule> notApplicable) {
		Severity severity = result.getSeverity();
		if (severity == Severity.NA) {
			notApplicable.add(new NotApplicableRule(rule.getId(), List.copyOf(rule.getRequiredEvents().keySet()),
					suggestedSettings(result)));
		} else if (severity != Severity.IGNORE) {
			findings.add(toFinding(rule, result, FindingSeverity.valueOf(severity.name())));
		}
	}

	static RuleFinding toFinding(IRule rule, IResult result, FindingSeverity severity) {
		RuleFinding finding = new RuleFinding();
		finding.setRuleId(rule.getId());
		finding.setRuleName(rule.getName());
		finding.setTopic(rule.getTopic());
		finding.setSeverity(severity);
		IQuantity score = result.getResult(TypedResult.SCORE);
		finding.setScore(score == null ? null : score.doubleValue());
		finding.setSummary(JmcMessageFormatter.format(result, result.getSummary()));
		finding.setExplanation(JmcMessageFormatter.format(result, result.getExplanation()));
		finding.setSolution(JmcMessageFormatter.format(result, result.getSolution()));
		return finding;
	}

	private static List<String> suggestedSettings(IResult result) {
		Collection<IRecordingSetting> settings = result.suggestRecordingSettings();
		if (settings == null) {
			return List.of();
		}
		return settings.stream()
				.map(s -> s.getSettingFor() + "#" + s.getSettingName() + "=" + s.getSettingValue())
				.sorted()
				.toList();
	}

	private static IResult await(IRule rule, Future<IResult> future) {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Execução das regras do JMC interrompida", e);
		} catch (ExecutionException e) {
			log.warn("Regra do JMC '{}' falhou e foi ignorada", rule.getId(), e.getCause());
			return null;
		}
	}
}
