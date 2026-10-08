package objectview.field;

import objectview.Viewable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * How a selected path that passes through an inherited level is read (#368). A search or
 * sort selection whose field inherits an ancestor's config has no finite list of paths:
 * the values sit at every level the inheritance reaches. The walk follows the selection
 * from a root, visiting each object at most once per config level, and yields every
 * value of the path in depth-first, list order.
 */
public interface PathWalk {

    /** Every occurrence of the path's value below {@code root}. With no presentation
     * context, no reached value is treated as a separate top-level card. */
    default List<Reached> read(Object root, Function<Viewable, FieldSchema> schemas) {
        return read(root, schemas, ignored -> false);
    }

    /** Every visible occurrence of the path's value below {@code root}. A scalar
     * reference to a {@code topLevel} object contributes only its caption and is not
     * traversed, exactly as rendering treats that occurrence as navigation-only. */
    List<Reached> read(Object root, Function<Viewable, FieldSchema> schemas,
                       Predicate<Viewable> topLevel);

    /**
     * One occurrence: the field's value where it was read, the path it renders at on the
     * card (the field names walked, inherited levels included), and the objects walked
     * through to reach it, which a search reveals to show the hit.
     */
    record Reached(Object value, FieldPath rendered, List<Viewable> route) {
        public Reached {
            route = route == null ? List.of() : List.copyOf(route);
        }
    }
}
