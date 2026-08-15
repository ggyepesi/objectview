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

    /**
     * Whether this object is a PART of another — it exists only within an owner, carries
     * that owner's identity, and is read as one of its owner's aspects.
     *
     * <p>Declared, not inferred: a renderer showing a part INSIDE its owner has already
     * said whose aspect it is, so repeating the part's own heading there is noise. Said
     * plainly by the object, no view has to recognise one by shape or by name.
     */
    default boolean isPart() { return false; }

    /** Stable class component of this object's identity. Usually identical to
     * {@link #typeName()}; a dynamically reclassified instance may retain its former
     * base class here so existing typed references and curation directives still
     * address the same object. */
    default String identityTypeName() { return typeName(); }

    /** Classes assigned directly to this instance. Inheritance is resolved by the
     * owning domain, because the base-class relation belongs to the domain schema.
     * Ordinary Java-backed objects have their concrete runtime class as their one
     * direct class; dynamic objects can persist several semantic class claims. */
    default java.util.Set<String> directClassNames() {
        String type = typeName();
        return type == null || type.isBlank()
                ? java.util.Set.of() : java.util.Set.of(type);
    }

    /** Absorb another instance's direct class claims — used when a merge survivor must
     * retain the duplicate's class. No-op for intrinsic Java classes; dynamic objects
     * use the stable identity and explicit hierarchy to normalize inherited claims. */
    default void absorbClasses(
            Viewable source, java.util.function.Function<String, String> baseType) { }

    /** This object's fields, backing-agnostic (declared reflection or a dynamic map). */
    FieldSet fields();
}
