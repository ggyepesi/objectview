package objectview.field;

import objectview.Viewable;

import java.util.List;

/**
 * Read-only fields supplied by the {@link Viewable} contract itself. Their names are
 * stable, reserved configuration keys; labels and semantic roles remain metadata.
 */
public final class ViewableContractFieldSet implements FieldSet {

    public static final String IDENTITY_KEY = "@view:identity";
    public static final String DISPLAY_KEY = "@view:display";

    private static final FieldRef IDENTITY = FieldRef.computed(
            IDENTITY_KEY, "Identifier", FieldKind.TEXT, FieldRole.IDENTITY);
    private static final FieldRef DISPLAY = FieldRef.computed(
            DISPLAY_KEY, "Name", FieldKind.TEXT, FieldRole.DISPLAY);
    private static final List<FieldRef> FIELDS = List.of(DISPLAY, IDENTITY);

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
        return switch (field.role()) {
            case IDENTITY -> viewable.getIdentifier();
            case DISPLAY -> viewable.getDisplayName();
            case NONE -> null;
        };
    }

    @Override public boolean has(String name) { return field(name) != null; }

    @Override public void write(String name, Object value) {
        throw new UnsupportedOperationException("Viewable contract fields are read-only: " + name);
    }
}
