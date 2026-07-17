package objectview.field;

import objectview.Viewable;

import java.util.List;

/**
 * The fields of a domain object, backing-agnostic: declared Java fields via
 * reflection ({@link ReflectionFieldSet}) or a dynamic property map ({@link
 * DynamicFieldSet}). This is the ONE interface the machinery reads — {@code
 * Card}, the config editors, the search index, the sort keys — so it
 * never branches on {@code instanceof DynamicFields}. Nothing is migrated onto
 * it yet; this is the seam. See #87.
 *
 * <p>Reads are single-level; dotted paths are composed by reading a reference and
 * wrapping its value with {@link #of} again (what {@code FieldAccess.getPath}
 * does).</p>
 */
public interface FieldSet {

    /** The object's fields, in a stable order. */
    List<FieldRef> fields();

    /**
     * The {@link FieldRef} named {@code name} (with its render hints), or null if
     * this object has no such field — the single-field counterpart of
     * {@link #fields()}.
     */
    default FieldRef field(String name) {
        for (FieldRef fr : fields()) {
            if (fr.name().equals(name)) {
                return fr;
            }
        }

        return null;
    }

    /** This instance's value for {@code name} (null if absent). */
    Object read(String name);

    /**
     * Whether {@code name} is a field of this backing at all — distinguishes a
     * present-but-null field from an absent one (which {@link #read} cannot), so
     * a caller can fall through to identity/other sources only when truly absent.
     */
    boolean has(String name);

    /**
     * Store {@code value} for {@code name} in this backing (a dynamic map entry
     * or a declared Java field). Throws when the backing has no such settable
     * field.
     */
    void write(String name, Object value);

    /**
     * A backing-appropriate FieldSet: the dynamic property map when present,
     * else declared-field reflection.
     */
    static FieldSet of(Viewable object) {
        return of(object, null);
    }

    /**
     * As {@link #of(Viewable)}, but a dynamic object is typed by {@code schema}
     * when given (a reflected object is self-describing, so the schema is
     * ignored).
     */
    static FieldSet of(Viewable object, FieldSchema schema) {
        return object instanceof DynamicFields dynamic
                ? new DynamicFieldSet(dynamic, schema)
                : new ReflectionFieldSet(object);
    }
}
