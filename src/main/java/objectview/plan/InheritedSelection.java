package objectview.plan;

import objectview.Viewable;
import objectview.field.FieldAccess;
import objectview.field.FieldPath;
import objectview.field.FieldSchema;
import objectview.field.PathWalk;
import objectview.viewconfig.ViewConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A search or sort selection read through the fields that inherit an ancestor's config
 * (#368). One leaf of the literal selection — a config level and a field ticked there —
 * is read at every object the selection reaches from a root: along the ticked fields as
 * usual, and through each inherited field back into its ancestor's level. Each object is
 * entered at most once per config level from one root, so a cycle ends and every walk
 * is finite. Values come in depth-first, list order: an object's own value first, then
 * what lies below it, member by member.
 */
final class InheritedSelection {

    private final ViewConfig root;
    // The levels from which the leaf's level can be reached, along ticks or inheritance.
    private final Map<ViewConfig, Boolean> reaching;
    private final ViewConfig leafLevel;
    private final String leafField;

    private InheritedSelection(ViewConfig root, ViewConfig leafLevel, String leafField) {
        this.root = root;
        this.leafLevel = leafLevel;
        this.leafField = leafField;
        this.reaching = reaching(root, leafLevel);
    }

    /** Whether {@code literal} has a field that inherits anywhere below it. */
    static boolean inherits(ViewConfig literal) {
        if (literal == null) return false;
        for (ViewConfig child : literal.getFields().values()) {
            if (child == null) continue;
            if (child.inheritedFrom() != null || inherits(child)) return true;
        }
        return false;
    }

    /** The walk reading {@code field} of {@code level} below {@code root}. */
    static PathWalk walk(ViewConfig root, ViewConfig level, String field) {
        InheritedSelection selection = new InheritedSelection(root, level, field);
        return selection::read;
    }

    private List<PathWalk.Reached> read(Object start, Function<Viewable, FieldSchema> schemas) {
        List<PathWalk.Reached> out = new ArrayList<>();
        Map<Object, Set<ViewConfig>> entered = new IdentityHashMap<>();
        visit(start, root, new ArrayList<>(), new ArrayList<>(), entered, schemas, out);
        return out;
    }

    private void visit(Object object, ViewConfig level, List<String> names,
                       List<Viewable> route, Map<Object, Set<ViewConfig>> entered,
                       Function<Viewable, FieldSchema> schemas, List<PathWalk.Reached> out) {
        if (object == null || !reaching.getOrDefault(level, false)) return;
        if (!entered.computeIfAbsent(object, ignored ->
                Collections.newSetFromMap(new IdentityHashMap<>())).add(level)) return;
        if (level == leafLevel) {
            Object value = read(object, leafField, schemas);
            if (value != null) {
                out.add(new PathWalk.Reached(value, path(names, leafField), route));
            }
        }
        for (Map.Entry<String, ViewConfig> ticked : level.getFields().entrySet()) {
            ViewConfig child = ticked.getValue();
            if (child == null) continue;
            ViewConfig next = child.effective();
            if (next.getFields().isEmpty() || !reaching.getOrDefault(next, false)) continue;
            Object value = read(object, ticked.getKey(), schemas);
            names.add(ticked.getKey());
            for (Object member : members(value)) {
                if (!(member instanceof Viewable viewable)) continue;
                route.add(viewable);
                visit(viewable, next, names, route, entered, schemas, out);
                route.remove(route.size() - 1);
            }
            names.remove(names.size() - 1);
        }
    }

    private static Object read(Object object, String field,
                               Function<Viewable, FieldSchema> schemas) {
        try {
            return FieldAccess.getPathValues(object, FieldPath.of(field), schemas);
        } catch (java.util.ConcurrentModificationException movedUnderneath) {
            throw movedUnderneath;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static Collection<?> members(Object value) {
        if (value instanceof Collection<?> collection) return collection;
        if (value instanceof Map<?, ?> map) return map.values();
        return value == null ? List.of() : List.of(value);
    }

    private static FieldPath path(List<String> names, String leaf) {
        List<String> segments = new ArrayList<>(names);
        segments.add(leaf);
        return FieldPath.of(segments.toArray(String[]::new));
    }

    /** Each level of {@code root}'s tree, true when {@code leaf} can be reached from it
     * through ticked object fields or inheritance. */
    private static Map<ViewConfig, Boolean> reaching(ViewConfig root, ViewConfig leaf) {
        List<ViewConfig> levels = new ArrayList<>();
        collect(root, levels, Collections.newSetFromMap(new IdentityHashMap<>()));
        Map<ViewConfig, Boolean> reaches = new IdentityHashMap<>();
        for (ViewConfig level : levels) reaches.put(level, level == leaf);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ViewConfig level : levels) {
                if (reaches.get(level)) continue;
                for (ViewConfig child : level.getFields().values()) {
                    if (child != null && reaches.getOrDefault(child.effective(), false)) {
                        reaches.put(level, true);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return reaches;
    }

    private static void collect(ViewConfig level, List<ViewConfig> out, Set<ViewConfig> seen) {
        if (level == null || !seen.add(level)) return;
        out.add(level);
        for (ViewConfig child : level.getFields().values()) {
            if (child != null && child.inheritedFrom() == null) collect(child, out, seen);
        }
    }
}
