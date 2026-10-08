package objectview.plan;

import objectview.field.FieldRef;

/**
 * The types on a config path, root first, each with what its level holds there (its
 * config, its editor row). The one rule for recursion (#368): a field whose declared
 * target type is already on the path has no config of its own and inherits the nearest
 * ancestor's of that type. The field editor marks such a field ↩, the View default and
 * the editor write the inheritance into the config, and search walks it — all through
 * {@link #inherited}, so they cannot disagree on which field recurses.
 *
 * <p>Types are compared by their declared names: a field's target type and the root's
 * logical type, never the class of the value an instance happens to hold.
 *
 * @param <T> what each level carries
 */
public final class ConfigChain<T> {

    private final String type;
    private final T level;
    private final ConfigChain<T> parent;

    private ConfigChain(String type, T level, ConfigChain<T> parent) {
        this.type = type;
        this.level = level;
        this.parent = parent;
    }

    /** A path holding only its root: {@code rootType} (null when unknown, so the root
     * is no field's ancestor) carrying {@code level}. */
    public static <T> ConfigChain<T> root(String rootType, T level) {
        return new ConfigChain<>(blankToNull(rootType), level, null);
    }

    /** This path one level deeper: {@code field}'s target type carrying {@code level}. */
    public ConfigChain<T> push(FieldRef field, T level) {
        return push(typeOf(field), level);
    }

    /** This path one level deeper: {@code type} carrying {@code level}. */
    public ConfigChain<T> push(String type, T level) {
        return new ConfigChain<>(blankToNull(type), level, this);
    }

    /** What the nearest level of {@code field}'s target type carries, or null when the
     * field has a config of its own. */
    public T inherited(FieldRef field) {
        return inherited(typeOf(field));
    }

    /** What the nearest level of {@code type} carries, or null when none is on the path. */
    public T inherited(String type) {
        String key = blankToNull(type);
        if (key == null) return null;
        for (ConfigChain<T> at = this; at != null; at = at.parent) {
            if (key.equals(at.type)) return at.level;
        }
        return null;
    }

    /** The declared type an object field leads to, or null for a non-object field. */
    public static String typeOf(FieldRef field) {
        if (field == null) return null;
        boolean object = field.reference() || field.embedded()
                || field.kind() == objectview.field.FieldKind.REFERENCE
                || field.valueKind() == objectview.field.FieldKind.REFERENCE;
        return object ? blankToNull(field.targetType()) : null;
    }

    private static String blankToNull(String type) {
        return type == null || type.isBlank() ? null : type;
    }
}
