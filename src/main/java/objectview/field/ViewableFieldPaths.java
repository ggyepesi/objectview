package objectview.field;

import objectview.Viewable;
import objectview.ViewableAdapter;

import objectview.media.ImagePane;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * One field path with its presentation metadata, and the rule for which Viewable
 * class a declared field leads to. The paths a config selects are read off its
 * literal ticks by {@link objectview.plan.LiteralPaths}; this class used to hold three
 * more collectors, each reading the shorthand with its own object rule.
 */
public final class ViewableFieldPaths {
    private ViewableFieldPaths() {}

    /** Presentation/metadata attached to one canonical access path. {@code valueKind} is
     *  the leaf's value kind (ORDERED / TEXT / …) — carried so consumers like sort know a
     *  field is numeric from the schema (a persisted {@code @Numeric}), not only from a
     *  reflection {@link Field} that a dynamic/snapshot path lacks. */
    public record PathInfo(String title, FieldPath path, Field leafField,
                           FieldKind valueKind, FieldRole role) {
        /** Derives {@code valueKind} from the leaf reflection field (UNKNOWN when none). */
        public PathInfo(String title, FieldPath path, Field leafField) {
            this(title, path, leafField, leafField == null
                            ? FieldKind.UNKNOWN : FieldKind.ofClass(leafField.getType()),
                    reflectedRole(leafField));
        }
        public PathInfo(String title, FieldPath path, Field leafField,
                        FieldKind valueKind) {
            this(title, path, leafField, valueKind, reflectedRole(leafField));
        }
        public String dotted() { return path.dotted(); }
        public String leaf() { return path.leaf(); }

        private static FieldRole reflectedRole(Field field) {
            return field == null ? FieldRole.NONE
                    : ReflectionFieldSet.describe(
                            field, field.getDeclaringClass()).role();
        }
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Viewable> nestedViewableClass(Field field) {
        if (field == null) {
            return null;
        }

        Class<?> type = field.getType();

        if (ImagePane.class.isAssignableFrom(type)) {
            return null;
        }

        if (Viewable.class.isAssignableFrom(type)) {
            return (Class<? extends Viewable>) type;
        }

        if (Collection.class.isAssignableFrom(type)) {
            Type g = field.getGenericType();

            if (g instanceof ParameterizedType pt) {
                Type arg = pt.getActualTypeArguments()[0];

                if (arg instanceof Class<?> c && Viewable.class.isAssignableFrom(c)) {
                    return (Class<? extends Viewable>) c;
                }
            }

            return null;
        }

        if (Map.class.isAssignableFrom(type)) {
            Type g = field.getGenericType();

            if (g instanceof ParameterizedType pt) {
                Type value = pt.getActualTypeArguments()[1];

                if (value instanceof Class<?> c && Viewable.class.isAssignableFrom(c)) {
                    return (Class<? extends Viewable>) c;
                }
            }

            return null;
        }

        return null;
    }
}
