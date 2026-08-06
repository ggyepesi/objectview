package objectview.field;

import objectview.Viewable;

import java.util.List;

/**
 * The fields of a domain object, backing-agnostic: declared Java fields via
 * reflection ({@link ReflectionFieldSet}) or a dynamic property map ({@link
 * DynamicFieldSet}). This is the ONE interface the machinery reads — {@code
 * Card}, the config editors, the search index, the sort keys — so those consumers
 * never branch on {@code instanceof DynamicFields}. See #87.
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
     * As {@link #of(Viewable)}, overlaid with an authoritative {@code schema}
     * when given. The same overlay is used for reflected and dynamic objects:
     * metadata therefore cannot change merely because the backing changed.
     */
    static FieldSet of(Viewable object, FieldSchema schema) {
        FieldSet backing;
        FieldSchema effective = schema;
        if (object instanceof DynamicFields dynamic) {
            // A dynamic object's map is its domain data, but annotation-declared
            // metadata on its Java base still belongs to the object. Layer only
            // presentation metadata; do not leak the map container itself.
            backing = new LayeredFieldSet(
                    new DynamicFieldSet(dynamic), new ReflectionFieldSet(object),
                    field -> field.link() || field.role() == FieldRole.DISPLAY);
            if (effective == null) {
                effective = dynamic.dynamicFieldSchema();
            }
        } else {
            backing = new ReflectionFieldSet(object);
        }
        if (effective != null) {
            backing = new SchemaFieldSet(backing, effective);
        }
        return ViewableContractFieldSet.overlay(object, backing);
    }
}
