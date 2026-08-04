package objectview.field;


/**
 * One field of a domain object — its name, value shape and display type — regardless
 * of whether the object is a reflected {@link objectview.Viewable} (declared Java fields)
 * or a dynamic property map ({@link DynamicFields}). The rendering / search /
 * sort / config machinery reads this instead of re-deriving field metadata two ways.
 *
 * <p>The <b>render hints</b> ({@link #inline()}, {@link #link()},
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

    /** Human-facing label, independent of the stable machine key returned by
     * {@link #name()}. Ordinary stored fields keep their name as the label. */
    default String label() { return name(); }

    /** The field's semantic role (identity / title / …), declared as metadata so consumers
     *  react to the ROLE, never to the name. Ordinary fields are {@link FieldRole#NONE}. */
    default FieldRole role() { return FieldRole.NONE; }

    /** The value shape (boolean / ordered / text / reference / collection). */
    FieldKind kind();

    /**
     * The scalar/element shape. For a scalar this normally equals {@link #kind()};
     * for a collection it describes one element (e.g. MEDIA for
     * {@code Collection<ImagePane>}). This prevents consumers from having to infer
     * an element's semantics from a display label or a currently present value.
     */
    FieldKind valueKind();

    /** A display type label, e.g. "Integer", "Category", "List&lt;Category&gt;" (or null). */
    String typeLabel();

    /** An entity-valued field (its value is a {@link objectview.Viewable}). */
    boolean reference();

    /** A multi-valued field (its value is a collection/array). */
    boolean collection();

    /** The logical type name of a referenced value/element, or null for a scalar. */
    String targetType();

    /** Plumbing retained in the data but omitted from ordinary field pickers and
     * nested traversal. */
    boolean structural();

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

    /** {@code @Reference} — force reference-chip rendering. */
    boolean annotatedReference();

    /** A field with no render hints — used for a dynamic (map-held) field, which has
     *  no annotations, and by callers that don't distinguish them. */
    static FieldRef of(String name, FieldKind kind, String typeLabel,
                       boolean reference, boolean collection, boolean minor) {
        return described(name, kind,
                inferredValueKind(kind, typeLabel, reference, collection),
                typeLabel, reference, collection, null, false, minor,
                false, false, "", false);
    }

    /** A declared field, carrying its annotation-derived render hints. */
    static FieldRef of(String name, FieldKind kind, String typeLabel,
                       boolean reference, boolean collection, boolean minor,
                       boolean inline, boolean link, String linkText,
                       boolean annotatedReference) {
        return described(name, kind,
                inferredValueKind(kind, typeLabel, reference, collection),
                typeLabel, reference, collection, null, false, minor,
                inline, link, linkText, annotatedReference);
    }

    /** Complete immutable field description used by compiled/reflected schemas. */
    static FieldRef described(String name, FieldKind kind, FieldKind valueKind,
                              String typeLabel, boolean reference,
                              boolean collection, String targetType,
                              boolean structural, boolean minor,
                              boolean inline, boolean link, String linkText,
                              boolean annotatedReference) {
        return described(name, name, FieldRole.NONE, kind, valueKind, typeLabel,
                reference, collection, targetType, structural, minor, inline,
                link, linkText, annotatedReference);
    }

    /** Complete description including presentation label and semantic role. */
    static FieldRef described(String name, String label, FieldRole role,
                              FieldKind kind, FieldKind valueKind,
                              String typeLabel, boolean reference,
                              boolean collection, String targetType,
                              boolean structural, boolean minor,
                              boolean inline, boolean link, String linkText,
                              boolean annotatedReference) {
        return new Impl(name, label == null ? name : label,
                role == null ? FieldRole.NONE : role,
                kind == null ? FieldKind.UNKNOWN : kind,
                valueKind == null ? FieldKind.UNKNOWN : valueKind,
                typeLabel, reference, collection, targetType, structural, minor,
                inline, link, linkText == null ? "" : linkText,
                annotatedReference);
    }

    /** Copy {@code field} while changing only its structural role. */
    static FieldRef withStructural(FieldRef field, boolean structural) {
        return described(field.name(), field.label(), field.role(),
                field.kind(), field.valueKind(),
                field.typeLabel(), field.reference(), field.collection(),
                field.targetType(), structural, field.minor(), field.inline(),
                field.link(), field.linkText(), field.annotatedReference());
    }

    /** Copy {@code field} under another stable key without losing its metadata. */
    static FieldRef withName(FieldRef field, String name) {
        return described(name, field.label(), field.role(),
                field.kind(), field.valueKind(), field.typeLabel(),
                field.reference(), field.collection(), field.targetType(),
                field.structural(), field.minor(), field.inline(), field.link(),
                field.linkText(), field.annotatedReference());
    }

    private static FieldKind inferredValueKind(
            FieldKind kind, String typeLabel, boolean reference,
            boolean collection) {
        if (!collection) {
            return kind == null ? FieldKind.UNKNOWN : kind;
        }
        if (reference) {
            return FieldKind.REFERENCE;
        }
        String label = typeLabel == null ? "" : typeLabel;
        int open = label.indexOf('<');
        int close = label.lastIndexOf('>');
        if (open >= 0 && close > open) {
            label = label.substring(open + 1, close).trim();
            int comma = label.lastIndexOf(',');
            if (comma >= 0) {
                label = label.substring(comma + 1).trim();
            }
        } else if (label.endsWith("[]")) {
            label = label.substring(0, label.length() - 2);
        }
        return FieldKind.ofTypeLabel(label);
    }

    record Impl(String name, String label, FieldRole role,
                FieldKind kind, FieldKind valueKind,
                String typeLabel, boolean reference, boolean collection,
                String targetType, boolean structural, boolean minor,
                boolean inline, boolean link, String linkText,
                boolean annotatedReference) implements FieldRef {}

    /** A COMPUTED/virtual field: ordinary metadata plus a semantic {@link FieldRole}, with no
     *  backing Java field or map entry — its value is produced by a reader in the FieldSet.
     *  Surfaces contract-derived data (identity/title) as an ordinary, role-tagged field. */
    static FieldRef computed(String name, String label, FieldKind kind, FieldRole role) {
        return new Computed(name, label == null ? name : label,
                kind == null ? FieldKind.UNKNOWN : kind,
                role == null ? FieldRole.NONE : role);
    }

    record Computed(String name, String label, FieldKind kind,
                    FieldRole role) implements FieldRef {
        @Override public FieldKind valueKind() { return kind; }
        @Override public String typeLabel() {
            return switch (kind) {
                case TEXT -> "String";
                case BOOLEAN -> "Boolean";
                case ORDERED -> "Number";
                case MEDIA -> "MediaValue";
                case REFERENCE -> "Viewable";
                default -> "Object";
            };
        }
        @Override public boolean reference() { return false; }
        @Override public boolean collection() { return false; }
        @Override public String targetType() { return null; }
        @Override public boolean structural() { return false; }
        @Override public boolean minor() { return false; }
        @Override public boolean inline() { return false; }
        @Override public boolean link() { return false; }
        @Override public String linkText() { return ""; }
        @Override public boolean annotatedReference() { return false; }
    }
}
