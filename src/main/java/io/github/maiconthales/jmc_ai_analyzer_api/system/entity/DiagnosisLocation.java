package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

import java.util.List;

/**
 * Onde o problema de um item de diagnóstico foi observado.
 *
 * @param thread      nome da thread, quando relevante
 * @param target      alvo de I/O ({@code host:porta} ou arquivo), quando relevante
 * @param stackFrames pilha usada para encontrar o frame da aplicação
 */
public record DiagnosisLocation(String thread, String target, List<StackFrame> stackFrames) {
}
