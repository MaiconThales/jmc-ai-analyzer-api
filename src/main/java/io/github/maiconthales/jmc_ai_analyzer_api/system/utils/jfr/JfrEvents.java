package io.github.maiconthales.jmc_ai_analyzer_api.system.utils.jfr;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.openjdk.jmc.common.item.IAccessorKey;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.IItemIterable;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.item.IType;
import org.openjdk.jmc.common.item.ItemFilters;

/** Iteração sobre eventos de um tipo, com acesso aos campos pelo nome. */
public final class JfrEvents {

	private JfrEvents() {
	}

	public static void forEach(IItemCollection items, String typeId, Consumer<EventView> consumer) {
		for (IItemIterable iterable : items.apply(ItemFilters.type(typeId))) {
			Map<String, IMemberAccessor<?, IItem>> accessors = accessors(iterable.getType());
			for (IItem item : iterable) {
				consumer.accept(new EventView(item, accessors));
			}
		}
	}

	public static long count(IItemCollection items, String typeId) {
		long total = 0;
		for (IItemIterable iterable : items.apply(ItemFilters.type(typeId))) {
			total += iterable.getItemCount();
		}
		return total;
	}

	public static boolean has(IItemCollection items, String typeId) {
		return items.apply(ItemFilters.type(typeId)).hasItems();
	}

	/** Quantidade de eventos por tipo, para a cobertura da gravação. */
	public static Map<String, Long> countByType(IItemCollection items) {
		Map<String, Long> counts = new HashMap<>();
		for (IItemIterable iterable : items) {
			counts.merge(iterable.getType().getIdentifier(), iterable.getItemCount(), Long::sum);
		}
		return counts;
	}

	private static Map<String, IMemberAccessor<?, IItem>> accessors(IType<IItem> type) {
		Map<String, IMemberAccessor<?, IItem>> accessors = new HashMap<>();
		for (IAccessorKey<?> key : type.getAccessorKeys().keySet()) {
			accessors.put(key.getIdentifier(), type.getAccessor(key));
		}
		return accessors;
	}
}
