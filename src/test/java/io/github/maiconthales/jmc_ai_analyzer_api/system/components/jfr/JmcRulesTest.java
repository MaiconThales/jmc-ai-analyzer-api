package io.github.maiconthales.jmc_ai_analyzer_api.system.components.jfr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.jfr.NotApplicableRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.JmcMessageFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openjdk.jmc.flightrecorder.rules.IRecordingSetting;
import org.openjdk.jmc.flightrecorder.rules.IResult;
import org.openjdk.jmc.flightrecorder.rules.IRule;
import org.openjdk.jmc.flightrecorder.rules.Severity;
import org.openjdk.jmc.flightrecorder.rules.util.RulesToolkit.EventAvailability;

class JmcRulesTest {

	@Test
	void sanitizeKeepsOnlyAllowedTagsWithoutAttributes() {
		assertThat(JmcMessageFormatter.sanitize(null)).isNull();
		assertThat(JmcMessageFormatter.sanitize("   ")).isNull();
		assertThat(JmcMessageFormatter.sanitize("<p class='x'>a<br/>b <a href='h'>link</a> <B>c</B></p>\t<script>x</script>"))
				.isEqualTo("<p>a b link <b>c</b></p> x");
		assertThat(JmcMessageFormatter.sanitize("<ul><li>1</li></ul><code>$</code>"))
				.isEqualTo("<ul><li>1</li></ul><code>$</code>");
		assertThat(JmcMessageFormatter.format(mock(IResult.class), null)).isNull();
		assertThat(JmcMessageFormatter.format(mock(IResult.class), " ")).isNull();
	}

	@Test
	void classifyIgnoresIgnoreAndKeepsNotApplicable() {
		IRule rule = mock(IRule.class);
		when(rule.getId()).thenReturn("R1");
		when(rule.getName()).thenReturn("Regra");
		when(rule.getTopic()).thenReturn("heap");
		when(rule.getRequiredEvents()).thenReturn(Map.of("jdk.X", EventAvailability.ENABLED));
		List<RuleFinding> findings = new ArrayList<>();
		List<NotApplicableRule> notApplicable = new ArrayList<>();

		JmcRuleRunner.classify(rule, result(Severity.IGNORE, null), findings, notApplicable);
		assertThat(findings).isEmpty();
		assertThat(notApplicable).isEmpty();

		IRecordingSetting setting = mock(IRecordingSetting.class);
		when(setting.getSettingFor()).thenReturn("jdk.X");
		when(setting.getSettingName()).thenReturn("enabled");
		when(setting.getSettingValue()).thenReturn("true");
		JmcRuleRunner.classify(rule, result(Severity.NA, List.of(setting)), findings, notApplicable);
		JmcRuleRunner.classify(rule, result(Severity.NA, null), findings, notApplicable);
		assertThat(notApplicable).extracting(NotApplicableRule::suggestedSettings)
				.containsExactly(List.of("jdk.X#enabled=true"), List.of());
		assertThat(notApplicable.get(0).requiredEvents()).containsExactly("jdk.X");

		JmcRuleRunner.classify(rule, result(Severity.OK, null), findings, notApplicable);
		assertThat(findings).singleElement().satisfies(f -> {
			assertThat(f.getRuleName()).isEqualTo("Regra");
			assertThat(f.getScore()).isNull();
			assertThat(f.getSummary()).isNull();
		});
	}

	private static IResult result(Severity severity, List<IRecordingSetting> settings) {
		IResult result = mock(IResult.class);
		when(result.getSeverity()).thenReturn(severity);
		when(result.suggestRecordingSettings()).thenReturn(settings);
		return result;
	}
}
