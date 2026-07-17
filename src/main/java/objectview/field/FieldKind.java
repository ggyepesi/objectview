package objectview.field;

import objectview.Viewable;

import java.util.Collection;
import java.util.Date;
import java.time.temporal.Temporal;

/**
 * The value shape of a {@link DomainField} — used to offer only the operators that
 * make sense for it (e.g. {@code <} / {@code between} for numbers and dates, {@code
 * contains} for text and collections) and to pick a facet/sort strategy. Populated
 * by the {@code DomainModel} that knows the field's type; {@link #UNKNOWN} allows
 * every operator — the safe fallback when the shape can't be determined.
 */
public enum FieldKind {
    BOOLEAN,      // true/false — is true / is false / equals
    ORDERED,      // number or date — comparisons + between
    TEXT,         // string — contains / starts with / ends with
    REFERENCE,    // an entity — equals / contains (by label)
    COLLECTION,   // multi-valued — contains / is empty
    UNKNOWN;

    /** Classify a runtime value (a representative field value). */
    public static FieldKind ofValue(Object v) {
        if (v == null) {
            return UNKNOWN;
        }
        if (v instanceof Boolean) {
            return BOOLEAN;
        }
        if (v instanceof Number) {
            return ORDERED;
        }
        if (isDate(v.getClass())) {
            return ORDERED;
        }
        if (v instanceof Collection) {
            return COLLECTION;
        }
        if (v instanceof Viewable) {
            return REFERENCE;
        }
        return TEXT;
    }

    /** Classify a declared Java field type (reflection domains). */
    public static FieldKind ofClass(Class<?> c) {
        if (c == null) {
            return UNKNOWN;
        }
        if (c.isPrimitive()) {
            if (c == boolean.class) {
                return BOOLEAN;
            }
            if (c == char.class || c == void.class) {
                return c == char.class ? TEXT : UNKNOWN;
            }
            return ORDERED;   // int, long, double, float, short, byte
        }
        if (c == Boolean.class) {
            return BOOLEAN;
        }
        if (Number.class.isAssignableFrom(c)) {
            return ORDERED;
        }
        if (isDate(c)) {
            return ORDERED;
        }
        if (Collection.class.isAssignableFrom(c)) {
            return COLLECTION;
        }
        if (Viewable.class.isAssignableFrom(c)) {
            return REFERENCE;
        }
        if (CharSequence.class.isAssignableFrom(c)) {
            return TEXT;
        }
        return UNKNOWN;
    }

    /** Classify a display type label (e.g. "Integer", "Boolean", "FlexibleDate"). */
    public static FieldKind ofTypeLabel(String label) {
        if (label == null) {
            return UNKNOWN;
        }
        String l = label.toLowerCase();
        if (l.contains("bool")) {
            return BOOLEAN;
        }
        if (l.contains("int") || l.contains("long") || l.contains("double")
                || l.contains("float") || l.contains("short") || l.contains("byte")
                || l.contains("number") || l.contains("decimal")) {
            return ORDERED;
        }
        if (l.contains("date") || l.contains("time") || l.contains("instant")) {
            return ORDERED;
        }
        if (l.contains("string") || l.contains("text") || l.contains("char")) {
            return TEXT;
        }
        return UNKNOWN;
    }

    private static boolean isDate(Class<?> c) {
        return Date.class.isAssignableFrom(c)
                || Temporal.class.isAssignableFrom(c)
                || c.getSimpleName().toLowerCase().contains("date");
    }
}
