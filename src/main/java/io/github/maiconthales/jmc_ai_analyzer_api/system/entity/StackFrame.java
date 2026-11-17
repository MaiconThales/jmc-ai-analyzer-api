package io.github.maiconthales.jmc_ai_analyzer_api.system.entity;

/**
 * Frame de pilha persistido em {@code jsonb}. Nomes curtos para economizar espaço.
 *
 * @param c classe (nome completo)
 * @param m método
 * @param d descritor do método
 * @param l linha, quando disponível
 */
public record StackFrame(String c, String m, String d, Integer l) {

	/** Representação legível: {@code classe.metodo:linha}. */
	public String display() {
		return c + "." + m + (l == null ? "" : ":" + l);
	}
}
