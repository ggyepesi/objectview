package objectview.field;

/**
 * Swing client-property keys used to tag a rendered component with the field it
 * came from — its display name, its dotted path, and its value. Rendering sets
 * these on each component; search reads them back to know what to match.
 *
 * <p>They live in the {@code field} package (which both {@code render} and
 * {@code search} already depend on) so that render can tag components without
 * depending on the search package — keeping the two acyclic.
 */
public final class FieldProperties {

    /** Client-property key: the field's dotted path (a {@code List<String>}). */
    public static final String FIELD_PATH_PROPERTY = "objectview.fieldPath";

    /** Client-property key: the field's display name. */
    public static final String FIELD_NAME_PROPERTY = "objectview.fieldName";

    /** Client-property key: the field's value. */
    public static final String FIELD_VALUE_PROPERTY = "objectview.fieldValue";

    private FieldProperties() {
    }
}
