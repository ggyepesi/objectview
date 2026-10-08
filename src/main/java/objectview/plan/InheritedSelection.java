package objectview.plan;

import objectview.Viewable;
import objectview.field.FieldAccess;
import objectview.field.FieldPath;
import objectview.field.FieldRef;
import objectview.field.FieldSchema;
import objectview.field.FieldSet;
import objectview.field.PathWalk;
import objectview.field.FieldRole;
import objectview.viewconfig.ViewConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

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
    private final boolean leafIsDisplay;

    private InheritedSelection(ViewConfig root, ViewConfig leafLevel, String leafField,
                               boolean leafIsDisplay) {
        this.root = root;
        this.leafLevel = leafLevel;
        this.leafField = leafField;
        this.leafIsDisplay = leafIsDisplay;
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
    static PathWalk walk(ViewConfig root, ViewConfig level, String field, FieldRole role) {
        InheritedSelection selection = new InheritedSelection(
                root, level, field, role == FieldRole.DISPLAY);
        return selection::read;
    }

    private List<PathWalk.Reached> read(Object start, Function<Viewable, FieldSchema> schemas,
                                        Predicate<Viewable> topLevel) {
        List<PathWalk.Reached> out = new ArrayList<>();
        Map<Object, Set<ViewConfig>> entered = new IdentityHashMap<>();
        visit(start, root, new ArrayList<>(), new ArrayList<>(), entered, schemas,
                topLevel == null ? ignored -> false : topLevel, out);
        return out;
    }

    private void visit(Object object, ViewConfig level, List<String> names,
                       List<Viewable> route, Map<Object, Set<ViewConfig>> entered,
                       Function<Viewable, FieldSchema> schemas,
                       Predicate<Viewable> topLevel, List<PathWalk.Reached> out) {
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
            boolean collection = value instanceof Collection<?> || value instanceof Map<?, ?>
                    || value != null && value.getClass().isArray();
            boolean navigation = !collection && object instanceof Viewable owner
                    && navigationReference(owner, ticked.getKey(), schemas);
            names.add(ticked.getKey());
            for (Object member : members(value)) {
                if (!(member instanceof Viewable viewable)) continue;
                route.add(viewable);
                if (navigation && topLevel.test(viewable)) {
                    readNavigationCaption(viewable, next, names, route, schemas, out);
                } else {
                    visit(viewable, next, names, route, entered, schemas, topLevel, out);
                }
                route.remove(route.size() - 1);
            }
            names.remove(names.size() - 1);
        }
    }

    /** A navigation-only scalar reference paints the target's selected DISPLAY and
     * nothing below it. Preserve that one searchable occurrence without walking into
     * fields which the containing card cannot reveal. */
    private void readNavigationCaption(Viewable target, ViewConfig level,
                                       List<String> names, List<Viewable> route,
                                       Function<Viewable, FieldSchema> schemas,
                                       List<PathWalk.Reached> out) {
        if (!leafIsDisplay || level != leafLevel) return;
        Object value = read(target, leafField, schemas);
        if (value != null) out.add(new PathWalk.Reached(
                value, path(names, leafField), route));
    }

    private static boolean navigationReference(
            Viewable owner, String fieldName, Function<Viewable, FieldSchema> schemas) {
        FieldRef field = FieldSet.of(owner, schemas.apply(owner)).field(fieldName);
        return field != null
                && PlanResolver.representation(field) == Representation.REFERENCE;
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
        if (value != null && value.getClass().isArray()) {
            List<Object> out = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) out.add(java.lang.reflect.Array.get(value, i));
            return out;
        }
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
