package objectview.field;

import objectview.Viewable;

import java.util.List;

/**
 * The field the {@link Viewable} contract projects for rendering, configuration,
 * search and sort: the DISPLAY name ({@link Viewable#getDisplayName()}), shown as
 * the panel title and offered — under the reserved key {@link #DISPLAY_KEY}, label
 * "Name" — as a configurable/searchable field.
 *
 * <p>{@link Viewable#getIdentifier()} is deliberately NOT projected as a field. It
 * is a keying method (identity / equality) that is never rendered — so there is
 * nothing to highlight, sort or configure. {@link #IDENTITY_KEY} stays a reserved
 * key so the data plane can recognise and never store it, but identity is a method,
 * not a field.
 */
public final class ViewableContractFieldSet implements FieldSet {

    /** Reserved key for the identity contract. NOT a projected field — see class doc. */
    public static final String IDENTITY_KEY = "@view:identity";
    public static final String DISPLAY_KEY = "@view:display";

    private static final FieldRef DISPLAY = FieldRef.computed(
            DISPLAY_KEY, "Name", FieldKind.TEXT, FieldRole.DISPLAY);
    private static final List<FieldRef> FIELDS = List.of(DISPLAY);

    private final Viewable viewable;

    public ViewableContractFieldSet(Viewable viewable) {
        this.viewable = viewable;
    }

    /** Metadata usable by a configuration editor even without a live instance. */
    public static List<FieldRef> fieldRefs() {
        return FIELDS;
    }

    /** Human label for a reserved key; ordinary keys remain unchanged. */
    public static String label(String key) {
        if (IDENTITY_KEY.equals(key)) return "Identifier";
        FieldRef field = FIELDS.stream()
                .filter(candidate -> candidate.name().equals(key))
                .findFirst()
                .orElse(null);
        return field == null ? key : field.label();
    }

    @Override public List<FieldRef> fields() { return FIELDS; }

    @Override public Object read(String name) {
        FieldRef field = field(name);
        if (field == null || viewable == null) return null;
        return field.role() == FieldRole.DISPLAY ? viewable.getDisplayName() : null;
    }

    @Override public boolean has(String name) { return field(name) != null; }

    @Override public void write(String name, Object value) {
        throw new UnsupportedOperationException("Viewable contract fields are read-only: " + name);
    }
}
