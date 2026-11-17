package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Conta as pilhas de uma chave e devolve a mais frequente. */
public final class FrameCounter {

	private final Map<List<StackFrame>, Long> counts = new HashMap<>();

	public void add(List<StackFrame> frames) {
		if (!frames.isEmpty()) {
			counts.merge(frames, 1L, Long::sum);
		}
	}

	public List<StackFrame> mostFrequent() {
		List<StackFrame> best = List.of();
		long bestCount = 0;
		for (Map.Entry<List<StackFrame>, Long> entry : counts.entrySet()) {
			if (entry.getValue() > bestCount) {
				best = entry.getKey();
				bestCount = entry.getValue();
			}
		}
		return best;
	}
}
