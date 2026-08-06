package objectview.field;

import objectview.ViewableAdapter;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Reads/writes fields by dotted path. DynamicFields-aware: for a
 * {@link DynamicFields} object (e.g. a saved snapshot's {@code
 * WikidataDynamicObject}) the map entries ARE the fields, so the transforms run
 * on the raw snapshot without recompiling the generated classes. Declared Java
 * fields are used otherwise (typed instances).
 */
public final class FieldAccess {

    private FieldAccess() {}

    public static Object getPath(Object root, String path) {
        Object current = root;
        for (String part : split(path)) {
            if (current == null) {
                return null;
            }
            // A collection/map intermediate (e.g. languages.name): descend into a
            // representative element so a nested path resolves instead of crashing.
            if (current instanceof Collection<?> c) {
                current = c.isEmpty() ? null : c.iterator().next();
            } else if (current instanceof Map<?, ?> m) {
                current = m.isEmpty() ? null : m.values().iterator().next();
            }
            if (current == null) {
                return null;
            }
            current = readField(current, part);
        }
        return current;
    }

    /**
     * Reads a path without collapsing collection-valued intermediates to an
     * arbitrary first element. This is the common read path for search, sort and
     * tabular cells: a nested path such as {@code languages.name} therefore yields
     * all language names, while a direct map/collection leaf remains intact.
     */
    public static Object getPathValues(Object root, List<String> path) {
        if (path == null || path.isEmpty()) {
            return root;
        }
        return getPathValues(root, path, 0);
    }

    private static Object getPathValues(Object current, List<String> path, int index) {
        if (current == null || index >= path.size()) {
            return current;
        }
        if (current instanceof Collection<?> values) {
            List<Object> result = new ArrayList<>();
            for (Object value : values) {
                addFlattened(result, getPathValues(value, path, index));
            }
            return result.isEmpty() ? null : result;
        }
        if (current instanceof Map<?, ?> values) {
            List<Object> result = new ArrayList<>();
            for (Object value : values.values()) {
                addFlattened(result, getPathValues(value, path, index));
            }
            return result.isEmpty() ? null : result;
        }
        if (current.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(current);
            for (int i = 0; i < length; i++) {
                addFlattened(result, getPathValues(
                        java.lang.reflect.Array.get(current, i), path, index));
            }
            return result.isEmpty() ? null : result;
        }
        return getPathValues(readField(current, path.get(index)), path, index + 1);
    }

    private static void addFlattened(List<Object> target, Object value) {
        if (value instanceof Collection<?> nested) {
            target.addAll(nested);
        } else if (value != null) {
            target.add(value);
        }
    }

    public static void setPath(Object root, String path, Object value) {
        var parts = split(path);
        writeField(owner(root, path), parts.get(parts.size() - 1), value);
    }

    public static void addToCollection(Object root, String fieldName, Object value) {
        if (root == null || value == null) {
            return;
        }
        Object current = readField(root, fieldName);

        Collection<Object> collection;
        if (current == null) {
            collection = new ArrayList<>();
            writeField(root, fieldName, collection);
        } else if (current instanceof Collection<?> c) {
            @SuppressWarnings("unchecked")
            Collection<Object> cast = (Collection<Object>) c;
            collection = cast;
        } else {
            throw new IllegalStateException(
                    "Field is not a collection: "
                            + root.getClass().getSimpleName() + "." + fieldName);
        }

        if (!collection.contains(value)) {
            collection.add(value);
        }
    }

    // --- field access, through the ONE FieldSet bridge (dynamic map or declared) ---

    private static Object readField(Object obj, String name) {
        // A Viewable reads through the ONE FieldSet bridge — a dynamic property map OR
        // declared Java fields behind one interface, no `instanceof DynamicFields`
        // fork (#87). has() distinguishes a present-but-null field (return its value)
        // from an absent one (fall through to a published view, then identity).
        if (obj instanceof objectview.Viewable q) {
            FieldSet fs = FieldSet.of(q);
            if (fs.has(name)) {
                return fs.read(name);   // a REAL field always wins
            }
            // Only then any addressable view the type publishes (e.g. value projections),
            // so a real field of the same name takes precedence generically — the ordering
            // guarantee lives here, not in each Addressable implementation.
            if (obj instanceof objectview.utils.Addressable a && a.viewNames().contains(name)) {
                return a.view(name);
            }
            return null;
        }
        if (obj instanceof objectview.utils.Addressable a && a.viewNames().contains(name)) {
            return a.view(name);
        }
        // A non-Viewable nested value (a heterogeneous map value, a JDK type, …):
        // reflect. Reading is tolerant — return null rather than crash a caller
        // enumerating/inspecting arbitrary domains.
        Field f = ViewableAdapter.getField(obj.getClass(), name);
        if (f != null) {
            try {
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception e) {
                throw new RuntimeException("Cannot read " + name + " from " + obj, e);
            }
        }
        return null;
    }

    private static void writeField(Object obj, String name, Object value) {
        // A Viewable writes through the ONE FieldSet bridge — a dynamic object stores a
        // projected view field in its property map (no compiled class needed), a typed
        // Viewable sets its declared field — with no `instanceof DynamicFields` fork (#87).
        if (obj instanceof objectview.Viewable q) {
            FieldSet.of(q).write(name, value);
            return;
        }
        // A non-Viewable nested owner (a plain POJO with a declared field): reflect.
        Field f = ViewableAdapter.getField(obj.getClass(), name);
        if (f != null) {
            try {
                f.setAccessible(true);
                f.set(obj, value);
                return;
            } catch (Exception e) {
                throw new RuntimeException("Cannot set " + name + " on " + obj, e);
            }
        }
        throw new IllegalArgumentException(
                "No field " + obj.getClass().getName() + "." + name);
    }

    private static Object owner(Object root, String path) {
        List<String> parts = split(path);
        Object current = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            if (current == null) {
                throw new IllegalStateException("Null owner while resolving " + path);
            }
            current = readField(current, parts.get(i));
        }
        return current;
    }

    private static List<String> split(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Empty field path");
        }
        return Arrays.asList(path.split("\\."));
    }
}
