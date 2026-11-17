package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import java.time.Instant;
import java.util.Map;
import org.openjdk.jmc.common.IMCOldObject;
import org.openjdk.jmc.common.IMCStackTrace;
import org.openjdk.jmc.common.IMCThread;
import org.openjdk.jmc.common.IMCType;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.common.util.LabeledIdentifier;

/**
 * Leitura tipada dos campos de um evento JFR pelo nome do campo (o mesmo do {@code jfr metadata}).
 * Campos inexistentes devolvem {@code null}.
 */
public final class EventView {

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private final IItem item;
	private final Map<String, IMemberAccessor<?, IItem>> accessors;

	EventView(IItem item, Map<String, IMemberAccessor<?, IItem>> accessors) {
		this.item = item;
		this.accessors = accessors;
	}

	public Object raw(String field) {
		IMemberAccessor<?, IItem> accessor = accessors.get(field);
		return accessor == null ? null : accessor.getMember(item);
	}

	public IQuantity quantity(String field) {
		return raw(field) instanceof IQuantity q ? q : null;
	}

	/** Percentual como fração de 0 a 1. */
	public Double fraction(String field) {
		IQuantity q = quantity(field);
		return q == null ? null : q.doubleValueIn(UnitLookup.PERCENT_UNITY);
	}

	public Long bytes(String field) {
		IQuantity q = quantity(field);
		return q == null ? null : q.clampedLongValueIn(UnitLookup.BYTE);
	}

	/** Intervalo de tempo em milissegundos. */
	public Double millis(String field) {
		IQuantity q = quantity(field);
		return q == null ? null : q.doubleValueIn(UnitLookup.MILLISECOND);
	}

	public Instant instant(String field) {
		IQuantity q = quantity(field);
		if (q == null) {
			return null;
		}
		long nanos = q.clampedLongValueIn(UnitLookup.EPOCH_NS);
		return Instant.ofEpochSecond(Math.floorDiv(nanos, NANOS_PER_SECOND), Math.floorMod(nanos, NANOS_PER_SECOND));
	}

	/** Número inteiro, venha ele como quantidade JMC ou como número bruto. */
	public Long number(String field) {
		Object value = raw(field);
		if (value instanceof IQuantity q) {
			return q.longValue();
		}
		return value instanceof Number n ? n.longValue() : null;
	}

	public String text(String field) {
		return raw(field) instanceof String s ? s : null;
	}

	public String className(String field) {
		Object value = raw(field);
		if (value instanceof IMCType type) {
			return type.getFullName();
		}
		return value instanceof IMCOldObject old && old.getType() != null ? old.getType().getFullName() : null;
	}

	public String threadName(String field) {
		return raw(field) instanceof IMCThread thread ? thread.getThreadName() : null;
	}

	/** Identificador do tipo de evento de um campo {@code labeledIdentifier} (ex.: {@code settingFor}). */
	public String identifier(String field) {
		return raw(field) instanceof LabeledIdentifier id ? id.getInterfaceId() : null;
	}

	public IMCStackTrace stackTrace() {
		return raw(JfrFields.STACK_TRACE) instanceof IMCStackTrace trace ? trace : null;
	}

	public Instant start() {
		return instant(JfrFields.START_TIME);
	}

	public String thread() {
		return threadName(JfrFields.EVENT_THREAD);
	}

	public double durationMs() {
		Double millis = millis(JfrFields.DURATION);
		return millis == null ? 0 : millis;
	}
}
