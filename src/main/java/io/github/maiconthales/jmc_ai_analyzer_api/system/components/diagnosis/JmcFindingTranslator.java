package io.github.maiconthales.jmc_ai_analyzer_api.system.components.diagnosis;

import io.github.maiconthales.jmc_ai_analyzer_api.system.dto.diagnosis.JmcTranslation;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.DiagnosisItem;
import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.RuleFinding;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.DiagnosisCategory;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.FindingSeverity;
import io.github.maiconthales.jmc_ai_analyzer_api.system.enums.Priority;
import io.github.maiconthales.jmc_ai_analyzer_api.system.properties.DiagnosisProperties;
import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.diagnosis.Diagnoses;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converte achados {@code INFO}/{@code WARNING} do JMC em itens de diagnóstico, com título e ação em português
 * para as regras traduzidas. As demais usam o nome e a solução originais.
 */
@Component
public class JmcFindingTranslator {


	public static final Map<String, JmcTranslation> TRANSLATIONS = Map.of(
			"Allocations.thread", new JmcTranslation(DiagnosisCategory.MEMORIA, "Thread com maior volume de alocação",
					"Revise o código executado por essa thread: montagem de mensagens, conversões para String e cópias "
							+ "de arrays.", "allocation"),
			"Allocations.class", new JmcTranslation(DiagnosisCategory.MEMORIA, "Tipo mais alocado",
					"Veja quem aloca esse tipo no top de alocação e reduza instâncias temporárias.", "allocation"),
			"HighGc", new JmcTranslation(DiagnosisCategory.GC, "Janela com a JVM pausada pelo GC",
					"Correlacione a janela com picos de alocação; revise o tamanho do heap e o coletor.", "gc"),
			"CompareCpu", new JmcTranslation(DiagnosisCategory.AMBIENTE, "Outros processos consumindo CPU",
					"As medições podem estar distorcidas; grave num ambiente isolado ou limite os outros processos.",
					"cpu"),
			"ManyRunningProcesses", new JmcTranslation(DiagnosisCategory.AMBIENTE, "Muitos processos na máquina",
					"Em servidores, mantenha apenas os processos necessários junto da aplicação.", "cpu"),
			"GcOptions", new JmcTranslation(DiagnosisCategory.CONFIG_JVM, "GC configurado com uma única thread",
					"Revise -XX:ActiveProcessorCount e -XX:ParallelGCThreads para que o GC use mais threads.", "gc"),
			"DuplicateFlags", new JmcTranslation(DiagnosisCategory.CONFIG_JVM, "Flags da JVM duplicadas",
					"Mantenha cada flag uma única vez; quando repetida, só a última vale.", "metadata"));

	public static final Map<String, DiagnosisCategory> TOPICS = Map.ofEntries(
			Map.entry("heap", DiagnosisCategory.MEMORIA),
			Map.entry("tlab", DiagnosisCategory.MEMORIA),
			Map.entry("memoryleak", DiagnosisCategory.MEMORIA),
			Map.entry("garbage_collection", DiagnosisCategory.GC),
			Map.entry("gc_configuration", DiagnosisCategory.GC),
			Map.entry("java_application", DiagnosisCategory.CPU),
			Map.entry("method_profiling", DiagnosisCategory.CPU),
			Map.entry("exceptions", DiagnosisCategory.EXCECOES),
			Map.entry("file_io", DiagnosisCategory.IO),
			Map.entry("socket_io", DiagnosisCategory.IO),
			Map.entry("lock_instances", DiagnosisCategory.LOCKS),
			Map.entry("biased_locking", DiagnosisCategory.LOCKS),
			Map.entry("jvm_information", DiagnosisCategory.CONFIG_JVM),
			Map.entry("agent_information", DiagnosisCategory.CONFIG_JVM),
			Map.entry("code_cache", DiagnosisCategory.CONFIG_JVM),
			Map.entry("classloading", DiagnosisCategory.CONFIG_JVM),
			Map.entry("vm_operations", DiagnosisCategory.CONFIG_JVM),
			Map.entry("recording", DiagnosisCategory.GRAVACAO));

	public boolean isDiagnosable(RuleFinding finding) {
		return finding.getSeverity() == FindingSeverity.INFO || finding.getSeverity() == FindingSeverity.WARNING;
	}

	public DiagnosisItem toItem(RuleFinding finding, DiagnosisProperties properties) {
		Priority priority = Priority.fromScore(finding.getScore());
		if (finding.getSeverity() == FindingSeverity.WARNING && priority == Priority.BAIXA) {
			priority = Priority.MEDIA;
		}
		int score = finding.getScore() == null
				? Diagnoses.defaultScore(priority, properties)
				: (int) Math.round(finding.getScore());
		JmcTranslation translation = TRANSLATIONS.get(finding.getRuleId());
		String evidence = finding.getSummary();
		if (translation != null) {
			return Diagnoses.item(priority, translation.category(), Diagnoses.JMC_PREFIX + finding.getRuleId(), score,
					translation.title(), evidence, null, translation.action(), translation.domainRef());
		}
		return Diagnoses.item(priority, TOPICS.getOrDefault(finding.getTopic(), DiagnosisCategory.AMBIENTE),
				Diagnoses.JMC_PREFIX + finding.getRuleId(), score, finding.getRuleName(), evidence, null,
				finding.getSolution() != null ? finding.getSolution() : finding.getExplanation(), "findings");
	}
}
