package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import io.github.maiconthales.jmc_ai_analyzer_api.system.entity.StackFrame;
import java.util.ArrayList;
import java.util.List;
import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCMethod;
import org.openjdk.jmc.common.IMCStackTrace;
import org.openjdk.jmc.common.util.FormatToolkit;

/** Conversão de pilhas do JMC para {@link StackFrame}. */
public final class StackFrames {

	public static final int MAX_FRAMES = 5;
	static final String UNKNOWN = "(desconhecido)";

	private StackFrames() {
	}

	/** Os {@value #MAX_FRAMES} frames do topo, ou lista vazia sem pilha. */
	public static List<StackFrame> top(IMCStackTrace trace) {
		if (trace == null || trace.getFrames() == null) {
			return List.of();
		}
		List<StackFrame> frames = new ArrayList<>(MAX_FRAMES);
		for (IMCFrame frame : trace.getFrames()) {
			if (frames.size() == MAX_FRAMES) {
				break;
			}
			frames.add(toFrame(frame));
		}
		return List.copyOf(frames);
	}

	/** Assinatura legível do método do topo da pilha, ou {@code null} sem pilha. */
	public static String topMethod(IMCStackTrace trace) {
		if (trace == null || trace.getFrames() == null || trace.getFrames().isEmpty()) {
			return null;
		}
		IMCMethod method = trace.getFrames().get(0).getMethod();
		return method == null ? UNKNOWN : FormatToolkit.getHumanReadable(method, false, false, true, true, true, false);
	}

	static StackFrame toFrame(IMCFrame frame) {
		IMCMethod method = frame.getMethod();
		if (method == null) {
			return new StackFrame(UNKNOWN, UNKNOWN, null, frame.getFrameLineNumber());
		}
		String type = method.getType() == null ? UNKNOWN : method.getType().getFullName();
		return new StackFrame(type, method.getMethodName(), method.getFormalDescriptor(), frame.getFrameLineNumber());
	}
}
