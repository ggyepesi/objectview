package objectview.render;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable, shallow container values for one Swing render pass. */
final class RenderSnapshot {
    private static final int ATTEMPTS = 3;

    private RenderSnapshot() {}

    static Object value(Object value) {
        if (value instanceof Collection<?> collection) return collection(collection);
        if (value instanceof Map<?, ?> map) return map(map);
        return value;
    }

    /**
     * ArrayList.toArray() copies its backing array without a fail-fast iterator. Other
     * collection implementations may still use one, so retry a short concurrent write
     * burst and render an empty value rather than taking down the EDT.
     */
    static List<Object> collection(Collection<?> source) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                Object[] values = source.toArray();
                List<Object> copy = new ArrayList<>(values.length);
                java.util.Collections.addAll(copy, values);
                return copy;
            } catch (ConcurrentModificationException ignored) {
                Thread.onSpinWait();
            }
        }
        return List.of();
    }

    static Map<Object, Object> map(Map<?, ?> source) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                Object[] entries = source.entrySet().toArray();
                Map<Object, Object> copy = new LinkedHashMap<>();
                for (Object item : entries) {
                    if (item instanceof Map.Entry<?, ?> entry) {
                        // Do not retain a live HashMap node as the rendered entry.
                        Map.Entry<Object, Object> stable = new AbstractMap.SimpleImmutableEntry<>(
                                entry.getKey(), entry.getValue());
                        copy.put(stable.getKey(), stable.getValue());
                    }
                }
                return copy;
            } catch (ConcurrentModificationException ignored) {
                Thread.onSpinWait();
            }
        }
        return Map.of();
    }
}
