package objectview.field;

import objectview.Viewable;

import java.util.List;

/**
 * Fallback field projected by the {@link Viewable} contract when no real field is
 * explicitly bound to {@link FieldRole#DISPLAY}. It is offered under the reserved
 * key {@link #DISPLAY_KEY}, labelled "Display label".
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
            DISPLAY_KEY, "Display label", FieldKind.TEXT, FieldRole.DISPLAY);
    private static final List<FieldRef> FIELDS = List.of(DISPLAY);

    private final Viewable viewable;

    public ViewableContractFieldSet(Viewable viewable) {
        this.viewable = viewable;
    }

    /** Metadata usable by a configuration editor even without a live instance. */
    public static List<FieldRef> fieldRefs() {
        return FIELDS;
    }

    static FieldRef displayFieldRef() { return DISPLAY; }

    /** The DISPLAY field key, or the reserved fallback key. If more than one field is
     *  bound to DISPLAY (a small user error), the LAST wins — deterministic, no crash. */
    public static String displayKey(FieldSet fields) {
        String found = DISPLAY_KEY;
        if (fields != null) {
            for (FieldRef field : fields.fields()) {
                if (field.role() == FieldRole.DISPLAY) found = field.name();
            }
        }
        return found;
    }

    /** Reflection-only counterpart used before an instance exists (last @DisplayField wins). */
    public static String displayKey(Class<? extends Viewable> type) {
        String found = DISPLAY_KEY;
        if (type != null) {
            for (java.lang.reflect.Field field : objectview.ViewableAdapter.getAllFields(type)) {
                if (field.isAnnotationPresent(objectview.annotations.DisplayField.class)) {
                    found = field.getName();
                }
            }
        }
        return found;
    }

    /** Bind the display alias to a real DISPLAY field, else layer the computed fallback.
     *  Multiple DISPLAY fields is a small user error, not a crash — see {@link #displayKey}. */
    static FieldSet overlay(Viewable viewable, FieldSet backing) {
        boolean hasDisplayField = backing.fields().stream()
                .anyMatch(field -> field.role() == FieldRole.DISPLAY);
        if (hasDisplayField) {
            return new DisplayBoundFieldSet(backing, viewable);
        }
        // A real field using the reserved key still owns it. This is unusual but
        // keeps FieldSet composition deterministic: real storage wins by key.
        if (backing.has(DISPLAY_KEY)) return backing;
        return new LayeredFieldSet(
                backing, new ViewableContractFieldSet(viewable), field -> true, false);
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
