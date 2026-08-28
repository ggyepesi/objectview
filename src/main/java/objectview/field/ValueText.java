package objectview.field;

import objectview.Viewable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The text a field value contributes, read the way the view reads it.
 *
 * <p>ONE traversal for the search index and the sort keys. Both used to carry their own
 * copy, and both collapsed a nested Viewable to its name, so text a card paints below the
 * row — a query log's requests live several steps down — could not be found by searching
 * for it. A nested object is now read through the same {@link FieldSet} the renderer draws
 * from, so search sees exactly what the card shows: {@code @Hidden} fields are absent from
 * that FieldSet, and therefore unfindable, without this class knowing the annotation
 * exists.
 *
 * <p>Depth is the only difference between the two consumers. A sort key IDENTIFIES a value
 * ({@code depth 0}: a Viewable is its name, exactly as a chip renders it), while search
 * reads what is on screen ({@link #NESTED_DEPTH}). Descent follows {@code inline}, because
 * that is where the card descends too — a reference is painted as a name chip and
 * contributes exactly that, which also bounds the walk to the object graph the row
 * actually displays.
 */
public final class ValueText {

    /**
     * How far search descends through inline nesting. A bound on COST, not on safety: the
     * visited set is what makes a cyclic graph terminate. Deep enough for a log tree,
     * whose steps nest one level per query group.
     */
    public static final int NESTED_DEPTH = 8;

    private ValueText() {
    }

    /**
     * Everything the value SHOWS, raw (callers normalize): inline objects expanded to
     * {@code depth} further levels, and a map's keys read alongside its values, because
     * a map paints both.
     */
    public static List<String> shown(Object value, int depth) {
        return collect(value, depth, true);
    }

    /**
     * What the value is IDENTIFIED by: a nested object reads as its name, exactly as the
     * chip that represents it, and a map as its values — a key labels a value, it does
     * not identify it. This is the reading a sort key needs.
     */
    public static List<String> identity(Object value) {
        return collect(value, 0, false);
    }

    private static List<String> collect(Object value, int depth, boolean labels) {
        List<String> out = new ArrayList<>();
        collect(value, depth, labels,
                Collections.newSetFromMap(new IdentityHashMap<>()), out);
        return out;
    }

    private static void collect(
            Object value, int depth, boolean labels,
            Set<Object> visited, List<String> out) {

        if (value == null) {
            return;
        }

        if (value instanceof Viewable v) {
            // The name identifies it wherever it appears; only the DESCENT is guarded,
            // so a second reference to one object still reads as that object.
            out.add(v.getName());

            if (depth <= 0 || !visited.add(v)) {
                return;
            }

            FieldSet fields = FieldSet.of(v);

            for (FieldRef field : fields.fields()) {
                // The card descends into an inline object and paints its fields; a
                // reference is drawn as a name chip, and reads as one here.
                collect(fields.read(field.name()),
                        field.embedded() ? depth - 1 : 0, labels, visited, out);
            }

            return;
        }

        if (value instanceof Collection<?> c) {
            for (Object item : c) {
                collect(item, depth, labels, visited, out);
            }

            return;
        }

        if (value instanceof Map<?, ?> m) {
            m.forEach((key, item) -> {
                if (labels) {
                    collect(key, depth, true, visited, out);
                }

                collect(item, depth, labels, visited, out);
            });

            return;
        }

        out.add(String.valueOf(value));
    }
}
