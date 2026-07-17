package objectview;

import objectview.field.FieldSet;

/**
 * The single input contract of the {@code objectview} widgets: anything that can be
 * VIEWED — its identity, its display name, its type label, and its fields (through the
 * {@link FieldSet} bridge). The card / search / view components read only this, so they
 * never see a host's concrete construct.
 *
 * <p>A host plugs in its own object by adapting it to {@code Viewable}: a hand-written
 * POJO, a dynamic property-map object, a graph node — each supplies identity/name/type
 * and a {@link FieldSet} over its fields (declared via reflection, or a map, or
 * anything else). {@code objectview.Viewable} is one such adapter.
 *
 * <p>The accessor names are deliberately the plain-getter ones (not "Viewable"-flavoured)
 * so a host's existing objects adapt with no call-site churn.
 */
public interface Viewable {

    /** Stable identity — map keys, selection, equality by identity. */
    String getIdentifier();

    /** Human-readable label shown in titles, chips and logging. */
    String getDisplayName();

    /** Alias of {@link #getDisplayName()} (some call sites read it as a "name"). */
    default String getName() { return getDisplayName(); }

    /** The type/dataset name this object is addressed and grouped by. Defaults to the
     *  Java class's simple name; dynamic objects override it with their domain name. */
    default String typeName() { return getClass().getSimpleName(); }

    /** This object's fields, backing-agnostic (declared reflection or a dynamic map). */
    FieldSet fields();
}
