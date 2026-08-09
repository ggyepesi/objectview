package objectview.field;

import objectview.Viewable;
import objectview.ViewableAdapter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves a field path without throwing away the object graph that produced its
 * values. Search and sort can consume {@link #value()}, while renderers and path
 * revelation consume the actual {@link Occurrence occurrences}, source
 * collections and nested Viewables.
 *
 * <p>This is deliberately different from merely returning a flattened list. A
 * path such as {@code languages.nativeName} has one scalar occurrence per
 * Language and also remembers the original {@code languages} collection. That
 * identity is what collapse/reveal state must be keyed by.</p>
 */
public final class ResolvedFieldPath {

    /** One real leaf reached in the source graph. */
    public record Occurrence(Object owner, Viewable renderOwner,
                             FieldRef field, Object value) {}

    private final FieldPath path;
    private final List<Occurrence> occurrences = new ArrayList<>();
    private final List<Object> containers = new ArrayList<>();
    private final List<Viewable> nestedViewables = new ArrayList<>();
    private IdentityHashMap<Object, BitSet> traversedAtIndex;

    private ResolvedFieldPath(FieldPath path) {
        this.path = path == null ? FieldPath.ROOT : path;
    }

    public static ResolvedFieldPath resolve(Object root, FieldPath path) {
        return resolve(root, path, ignored -> null);
    }

    public static ResolvedFieldPath resolve(
            Object root, FieldPath path,
            Function<Viewable, FieldSchema> schemaResolver) {
        ResolvedFieldPath result = new ResolvedFieldPath(path);
        if (root == null || result.path.isRoot()) return result;
        result.walk(root, 0, root instanceof Viewable q ? q : null,
                root, schemaResolver == null ? ignored -> null : schemaResolver);
        return result;
    }

    public FieldPath path() { return path; }
    public List<Occurrence> occurrences() { return List.copyOf(occurrences); }
    public List<Object> containers() { return List.copyOf(containers); }
    public List<Viewable> nestedViewables() { return List.copyOf(nestedViewables); }

    /** First source collection/map on the path, suitable as the cell's collapse key. */
    public Object primaryContainer() {
        return containers.isEmpty() ? null : containers.get(0);
    }

    /**
     * Data-centric projection used by search/sort. A single direct leaf retains
     * its original value (including its collection identity); multiple leaf
     * occurrences are flattened into their values.
     */
    public Object value() {
        if (occurrences.isEmpty()) return null;
        if (occurrences.size() == 1) return occurrences.get(0).value();
        List<Object> values = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            addFlattened(values, occurrence.value());
        }
        return values.isEmpty() ? null : values;
    }

    private void walk(Object current, int index, Viewable nearestViewable,
                      Object root,
                      Function<Viewable, FieldSchema> schemaResolver) {
        if (current == null || index >= path.size()) return;

        if (current instanceof Collection<?> collection) {
            if (!enter(current, index)) return;
            rememberContainer(current);
            for (Object value : collection) {
                walk(value, index, nearestViewable, root, schemaResolver);
            }
            return;
        }
        if (current instanceof Map<?, ?> map) {
            if (!enter(current, index)) return;
            rememberContainer(current);
            for (Object value : map.values()) {
                walk(value, index, nearestViewable, root, schemaResolver);
            }
            return;
        }
        if (current.getClass().isArray()) {
            if (!enter(current, index)) return;
            for (int i = 0; i < Array.getLength(current); i++) {
                walk(Array.get(current, i), index, nearestViewable, root,
                        schemaResolver);
            }
            return;
        }

        String segment = path.segments().get(index);
        if (current instanceof Viewable viewable) {
            if (current != root && rememberIdentity(nestedViewables, viewable)) {
                nestedViewables.add(viewable);
            }
            FieldSet fields = FieldSet.of(viewable, schemaResolver.apply(viewable));
            FieldRef field = fields.field(segment);
            boolean addressable = viewable instanceof objectview.utils.Addressable a
                    && a.viewNames().contains(segment);
            if (field == null && !addressable) return;
            Object value = FieldAccess.readField(viewable, segment);
            if (field == null) field = described(segment, value);
            if (index == path.size() - 1) {
                rememberLeafContainer(value);
                occurrences.add(new Occurrence(viewable, viewable, field, value));
            } else {
                walk(value, index + 1, viewable, root, schemaResolver);
            }
            return;
        }

        Field reflected = ViewableAdapter.getField(current.getClass(), segment);
        if (reflected == null) return;
        try {
            reflected.setAccessible(true);
            Object value = reflected.get(current);
            FieldRef field = ReflectionFieldSet.describe(
                    reflected, reflected.getDeclaringClass());
            if (index == path.size() - 1) {
                rememberLeafContainer(value);
                occurrences.add(new Occurrence(
                        current, nearestViewable, field, value));
            } else {
                walk(value, index + 1, nearestViewable, root, schemaResolver);
            }
        } catch (ReflectiveOperationException ignored) {
            // Arbitrary nested values are inspected tolerantly, like FieldAccess.
        }
    }

    private void rememberContainer(Object container) {
        if (rememberIdentity(containers, container)) containers.add(container);
    }

    private void rememberLeafContainer(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            rememberContainer(value);
        }
    }

    private boolean enter(Object value, int index) {
        if (traversedAtIndex == null) traversedAtIndex = new IdentityHashMap<>();
        BitSet indices = traversedAtIndex.computeIfAbsent(
                value, ignored -> new BitSet(path.size()));
        if (indices.get(index)) return false;
        indices.set(index);
        return true;
    }

    private static boolean rememberIdentity(List<?> values, Object candidate) {
        for (Object value : values) {
            if (value == candidate) return false;
        }
        return true;
    }

    private static void addFlattened(List<Object> target, Object value) {
        if (value instanceof Collection<?> collection) {
            target.addAll(collection);
        } else if (value != null) {
            target.add(value);
        }
    }

    private static FieldRef described(String name, Object value) {
        FieldKind kind = FieldKind.ofValue(value);
        boolean collection = kind == FieldKind.COLLECTION;
        return FieldRef.described(
                name, ViewableContractFieldSet.label(name), FieldRole.NONE,
                kind, kind, null, kind == FieldKind.REFERENCE, collection, null,
                false, false, false, false, "", false);
    }
}
