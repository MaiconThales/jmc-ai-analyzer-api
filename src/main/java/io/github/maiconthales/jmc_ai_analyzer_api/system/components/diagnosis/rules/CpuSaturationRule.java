package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.rules;

import io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis.DiagnosisRule;
import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.DiagnosisContext;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** CPU da máquina saturada por uma parte relevante da gravação. */
@Component
public class CpuSaturationRule implements DiagnosisRule {

	@Override
	public String id() {
		return "CPU_SATURATION";
	}

	@Override
	public Optional<DiagnosisItem> evaluate(DiagnosisContext ctx) {
		var cpu = ctx.extraction().cpu();
		if (!cpu.available() || cpu.machineValues().isEmpty()) {
			return Optional.empty();
		}
		double limit = ctx.properties().cpuSaturationPct() / 100;
		long saturated = cpu.machineValues().stream().filter(v -> v > limit).count();
		double pct = saturated * 100.0 / cpu.machineValues().size();
		if (pct <= ctx.properties().cpuSaturationTimePct()) {
			return Optional.empty();
		}
		return Optional.of(Diagnoses.app(this, Priority.ALTA, DiagnosisCategory.CPU, ctx.properties(),
				"CPU da máquina saturada",
				"A máquina ficou acima de " + Diagnoses.number(ctx.properties().cpuSaturationPct()) + "% de CPU em "
						+ Diagnoses.number(pct) + "% das medições; a JVM usou em média "
						+ Diagnoses.number(cpu.jvmAvg() * 100) + "%.",
				null,
				"Se a JVM usa pouco da CPU, outros processos competem pela máquina; senão, veja os hot methods e a CPU "
						+ "por thread.",
				"cpu"));
	}
}
