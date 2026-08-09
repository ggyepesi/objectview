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
        return getPath(root, FieldPath.parse(path));
    }

    public static Object getPath(Object root, FieldPath path) {
        Object current = root;
        for (String part : path.segments()) {
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
    public static Object getPathValues(Object root, FieldPath path) {
        if (path == null || path.isRoot()) return root;
        return ResolvedFieldPath.resolve(root, path).value();
    }

    public static void setPath(Object root, String path, Object value) {
        setPath(root, FieldPath.parse(path), value);
    }

    public static void setPath(Object root, FieldPath path, Object value) {
        if (path == null || path.isRoot()) {
            throw new IllegalArgumentException("Empty field path");
        }
        writeField(owner(root, path), path.leaf(), value);
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

    static Object readField(Object obj, String name) {
        // A Viewable reads through the ONE FieldSet bridge — a dynamic property map OR
        // declared Java fields behind one interface, no `instanceof DynamicFields`
        // fork (#87). has() distinguishes a present-but-null field (return its value)
        // from an absent one (fall through to a published view, then identity).
        if (obj instanceof objectview.Viewable q) {
            FieldSet fs = FieldSet.of(q);
            if (fs.has(name)) {
                Object value = fs.read(name);
                if (value != null) {
                    return value;   // a REAL stored value always wins
                }
                // Present-but-null: the DISPLAY field may be DECLARED by the schema yet not
                // STORED — on a snapshot a reference's display name is getDisplayName(), not a
                // map entry. Resolve it the same way rendering does so a path to a reference's
                // display field (e.g. languages.name) reads the name instead of null.
                if (isDisplayField(fs, name)) {
                    return q.getDisplayName();
                }
                return null;   // a real present-but-null field keeps precedence over any view
            }
            // An absent field: the unstored display field resolves to getDisplayName() too.
            // Recognized by ROLE (the type's DISPLAY field or the reserved contract key),
            // never by the literal field name.
            if (isDisplayField(fs, name)) {
                return q.getDisplayName();
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

    /** Whether {@code name} addresses the object's DISPLAY field — the last field bound to
     *  {@link FieldRole#DISPLAY}, or the reserved contract key when nothing is. Role-based,
     *  so a snapshot's unstored display name resolves through {@link
     *  objectview.Viewable#getDisplayName()} exactly as rendering does. */
    private static boolean isDisplayField(FieldSet fs, String name) {
        return name.equals(ViewableContractFieldSet.displayKey(fs))
                || ViewableContractFieldSet.DISPLAY_KEY.equals(name);
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

    private static Object owner(Object root, FieldPath path) {
        List<String> parts = path.segments();
        Object current = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            if (current == null) {
                throw new IllegalStateException("Null owner while resolving " + path);
            }
            current = readField(current, parts.get(i));
        }
        return current;
    }

}
