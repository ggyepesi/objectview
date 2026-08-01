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

    /**
     * Label used when this object appears as a reference in another object's
     * field. Most objects use their ordinary name; hierarchical structural
     * objects may qualify it with ancestry so a leaf is not mistaken for a
     * top-level value.
     */
    default String getReferenceLabel() { return getName(); }

    /** The type/dataset name this object is addressed and grouped by. Defaults to the
     *  Java class's simple name; dynamic objects override it with their domain name. */
    default String typeName() { return getClass().getSimpleName(); }

    /** Classes assigned directly to this instance. Inheritance is resolved by the
     * owning domain, because the base-class relation belongs to the domain schema.
     * Ordinary Java-backed objects have their concrete runtime class as their one
     * direct class; dynamic objects can persist several semantic class claims. */
    default java.util.Set<String> directClassNames() {
        String type = typeName();
        return type == null || type.isBlank()
                ? java.util.Set.of() : java.util.Set.of(type);
    }

    /** Absorb additional direct class claims into this instance — used when a merge
     *  survivor must keep a duplicate's more-specific subclass claim. No-op for objects
     *  whose class is intrinsic (a reflection object's Java type is fixed); dynamic
     *  objects union the names into their persisted claim set. */
    default void absorbClasses(java.util.Collection<String> classNames) { }

    /** This object's fields, backing-agnostic (declared reflection or a dynamic map). */
    FieldSet fields();
}
