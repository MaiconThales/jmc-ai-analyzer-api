package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr.extractors.IoExtractorAccess;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openjdk.jmc.common.IMCOldObject;
import org.openjdk.jmc.common.IMCThread;
import org.openjdk.jmc.common.IMCType;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.common.util.LabeledIdentifier;

class EventViewTest {

	private final IItem item = mock(IItem.class);
	private final Map<String, IMemberAccessor<?, IItem>> accessors = new HashMap<>();

	@Test
	void missingOrWrongTypedFieldsReturnNull() {
		put("text", 10L);
		EventView view = new EventView(item, accessors);
		assertThat(view.raw("nada")).isNull();
		assertThat(view.quantity("text")).isNull();
		assertThat(view.fraction("nada")).isNull();
		assertThat(view.bytes("nada")).isNull();
		assertThat(view.millis("nada")).isNull();
		assertThat(view.instant("nada")).isNull();
		assertThat(view.number("nada")).isNull();
		assertThat(view.text("text")).isNull();
		assertThat(view.className("text")).isNull();
		assertThat(view.threadName("text")).isNull();
		assertThat(view.identifier("text")).isNull();
		assertThat(view.stackTrace()).isNull();
		assertThat(view.start()).isNull();
		assertThat(view.thread()).isNull();
		assertThat(view.durationMs()).isZero();
		assertThat(view.number("text")).isEqualTo(10L);
	}

	@Test
	void typedValuesAreConverted() {
		IMCType type = mock(IMCType.class);
		when(type.getFullName()).thenReturn("com.A");
		IMCOldObject old = mock(IMCOldObject.class);
		when(old.getType()).thenReturn(type);
		IMCOldObject oldWithoutType = mock(IMCOldObject.class);
		IMCThread thread = mock(IMCThread.class);
		when(thread.getThreadName()).thenReturn("main");
		put("type", type);
		put("old", old);
		put("oldNoType", oldWithoutType);
		put("eventThread", thread);
		put("id", new LabeledIdentifier("jdk.CPULoad", 1, "CPU", "d"));
		put("count", UnitLookup.NUMBER_UNITY.quantity(5));
		put("text", "t");
		EventView view = new EventView(item, accessors);
		assertThat(view.className("type")).isEqualTo("com.A");
		assertThat(view.className("old")).isEqualTo("com.A");
		assertThat(view.className("oldNoType")).isNull();
		assertThat(view.thread()).isEqualTo("main");
		assertThat(view.identifier("id")).isEqualTo("jdk.CPULoad");
		assertThat(view.number("count")).isEqualTo(5L);
		assertThat(view.text("text")).isEqualTo("t");
		assertThat(IoExtractorAccess.socket(view)).isEqualTo("(desconhecido)");
		assertThat(IoExtractorAccess.file(view)).isEqualTo("(desconhecido)");
	}

	@Test
	void socketAndFileTargets() {
		put("address", "10.0.0.1");
		put("port", 5432L);
		put("path", " ");
		EventView view = new EventView(item, accessors);
		assertThat(IoExtractorAccess.socket(view)).isEqualTo("10.0.0.1:5432");
		assertThat(IoExtractorAccess.file(view)).isEqualTo("(desconhecido)");
		put("host", "db");
		put("path", "/tmp/a");
		view = new EventView(item, accessors);
		assertThat(IoExtractorAccess.socket(view)).isEqualTo("db:5432");
		assertThat(IoExtractorAccess.file(view)).isEqualTo("/tmp/a");
		put("host", "");
		put("address", "");
		accessors.remove("port");
		view = new EventView(item, accessors);
		assertThat(IoExtractorAccess.socket(view)).isEqualTo("(desconhecido)");
	}

	@SuppressWarnings("unchecked")
	private void put(String field, Object value) {
		IMemberAccessor<Object, IItem> accessor = mock(IMemberAccessor.class);
		when(accessor.getMember(item)).thenReturn(value);
		accessors.put(field, accessor);
	}
}
