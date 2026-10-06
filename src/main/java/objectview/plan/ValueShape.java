package objectview.plan;

import objectview.Viewable;
import objectview.media.MediaValue;

import java.util.Collection;
import java.util.Map;

/** Classifies a value the schema could not: a field resolved as
 * {@link Representation#VALUE}, or a collection member. The only shape-by-value rule. */
final class ValueShape {

    private ValueShape() {}

    static Representation of(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return Representation.COLLECTION;
        }
        if (value instanceof Viewable) return Representation.REFERENCE;
        if (value instanceof MediaValue) return Representation.MEDIA;
        return Representation.TEXT;
    }

    static Collection<?> members(Object collection) {
        if (collection instanceof Map<?, ?> map) return map.values();
        return collection instanceof Collection<?> items ? items : java.util.List.of();
    }
}
