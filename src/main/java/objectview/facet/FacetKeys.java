package objectview.facet;

import objectview.Viewable;
import objectview.ViewableAdapter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Reflective extraction of facet keys from a field value. */
final class FacetKeys {

    private FacetKeys() {}

    static List<String> fromField(Viewable q, String fieldPath) {
        List<String> out = new ArrayList<>();
        for (Object leaf : resolve(q, fieldPath)) {
            out.addAll(keysOf(leaf));
        }
        return out;
    }

    /** The {@link Viewable} value(s) of a field — for reference facets. */
    static List<Viewable> refsFromField(Viewable q, String fieldPath) {
        List<Viewable> out = new ArrayList<>();
        for (Object leaf : resolve(q, fieldPath)) {
            out.addAll(refsOf(leaf));
        }
        return out;
    }

    /**
     * Resolves a (possibly dotted) field path to its leaf value(s), fanning OUT
     * through collections/maps at each hop — so {@code languages.name} yields every
     * language's name, and {@code nominee.name} the nominee's name. A single-segment
     * path returns that field's value(s), identical to a direct read.
     */
    private static List<Object> resolve(Viewable q, String fieldPath) {
        List<Object> current = new ArrayList<>();
        current.add(q);
        for (String segment : fieldPath.split("\\.")) {
            List<Object> next = new ArrayList<>();
            for (Object obj : current) {
                addFlattened(next, readField(obj, segment));
            }
            current = next;
        }
        return current;
    }

    private static Object readField(Object obj, String name) {
        // A Viewable reads through the ONE FieldSet bridge (#87) — a dynamic property
        // map or declared Java fields, no `instanceof DynamicFields` fork. Facet paths
        // are real field names, so a plain read (null when absent) is all we need.
        if (obj instanceof Viewable q) {
            return objectview.field.FieldSet.of(q).read(name);
        }
        Field f = ViewableAdapter.getField(obj.getClass(), name);
        if (f == null) {
            return null;
        }
        try {
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static void addFlattened(List<Object> out, Object v) {
        if (v == null) {
            return;
        }
        if (v instanceof Collection<?> c) {
            for (Object item : c) {
                addFlattened(out, item);
            }
        } else if (v instanceof Map<?, ?> m) {
            for (Object item : m.values()) {
                addFlattened(out, item);
            }
        } else {
            out.add(v);
        }
    }

    private static List<Viewable> refsOf(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Viewable q) {
            return List.of(q);
        }
        List<Viewable> out = new ArrayList<>();
        if (value instanceof Collection<?> c) {
            for (Object item : c) {
                out.addAll(refsOf(item));
            }
        } else if (value instanceof Map<?, ?> m) {
            for (Object item : m.values()) {
                out.addAll(refsOf(item));
            }
        }
        return out;
    }

    static List<String> keysOf(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Viewable q) {
            return single(q.getDisplayName());
        }
        if (value instanceof Collection<?> c) {
            List<String> out = new ArrayList<>();
            for (Object item : c) {
                out.addAll(keysOf(item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> m) {
            List<String> out = new ArrayList<>();
            for (Object item : m.values()) {
                out.addAll(keysOf(item));
            }
            return out;
        }
        return single(String.valueOf(value));
    }

    private static List<String> single(String s) {
        return s == null || s.isBlank() ? List.of() : List.of(s);
    }
}
