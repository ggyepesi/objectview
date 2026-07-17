package objectview.field;


/**
 * One field of a domain object — its name, value shape and display type — regardless
 * of whether the object is a reflected {@link objectview.Viewable} (declared Java fields)
 * or a dynamic property map ({@link DynamicFields}). The rendering / search /
 * sort / config machinery reads this instead of re-deriving field metadata two ways.
 *
 * <p>The <b>render hints</b> ({@link #inline()}, {@link #link()}, {@link #provenance()},
 * {@link #annotatedReference()}) are annotation-derived and so only a <i>declared</i>
 * field can carry them — a dynamic map value has no annotations and reports them all
 * false / empty. A single render builder reads these hints first, then falls back to
 * the value's shape (which is backing-agnostic), so it needs no {@code instanceof
 * DynamicFields} fork. See #87.
 *
 * <p>Traversal into a reference is composition, not a method here: read the value and
 * wrap it — {@code FieldSet.of((Viewable) set.read("nominee"))} — mirroring {@code
 * FieldAccess.getPath}.
 */
public interface FieldRef {

    String name();

    /** The value shape (boolean / ordered / text / reference / collection). */
    FieldKind kind();

    /** A display type label, e.g. "Integer", "Category", "List&lt;Category&gt;" (or null). */
    String typeLabel();

    /** An entity-valued field (its value is a {@link objectview.Viewable}). */
    boolean reference();

    /** A multi-valued field (its value is a collection/array). */
    boolean collection();

    /** A minor field — a rendering hint (compact / hidden-by-default), if the backing
     *  distinguishes them; false when it doesn't. */
    boolean minor();

    // --- render hints (annotation-derived; a dynamic field reports false / "") ------

    /** {@code @Inline} — render the referent(s) fully expanded inline. */
    boolean inline();

    /** {@code @Link} — the (String) value is an external URL to render as a link. */
    boolean link();

    /** The {@code @Link} display text (empty when none / not a link field). */
    String linkText();

    /** {@code @Provenance} — a source/metadata field (chip; skipped by reference walks). */
    boolean provenance();

    /** {@code @Reference} — force reference-chip rendering. */
    boolean annotatedReference();

    /** A field with no render hints — used for a dynamic (map-held) field, which has
     *  no annotations, and by callers that don't distinguish them. */
    static FieldRef of(String name, FieldKind kind, String typeLabel,
                       boolean reference, boolean collection, boolean minor) {
        return new Impl(name, kind, typeLabel, reference, collection, minor,
                false, false, "", false, false);
    }

    /** A declared field, carrying its annotation-derived render hints. */
    static FieldRef of(String name, FieldKind kind, String typeLabel,
                       boolean reference, boolean collection, boolean minor,
                       boolean inline, boolean link, String linkText,
                       boolean provenance, boolean annotatedReference) {
        return new Impl(name, kind, typeLabel, reference, collection, minor,
                inline, link, linkText, provenance, annotatedReference);
    }

    record Impl(String name, FieldKind kind, String typeLabel,
                boolean reference, boolean collection, boolean minor,
                boolean inline, boolean link, String linkText,
                boolean provenance, boolean annotatedReference) implements FieldRef {}
}
