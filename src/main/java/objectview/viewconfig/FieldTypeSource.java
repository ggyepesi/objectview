package objectview.viewconfig;

/**
 * Optional typed-field info for {@link ViewConfigEditor} — a generic
 * seam letting a caller override the editor's sample reflection with authoritative
 * types (e.g. a compiled model schema). Purely mechanical: the editor asks for a
 * field's display type, whether it's structural (hide it), and the source for its
 * children; it has no idea where the answers come from. When {@link #field} returns
 * {@code null} the editor falls back to reflecting the sample, so this never has to
 * describe every field — only the ones it knows.
 */
public interface FieldTypeSource {

    /** Type info for the field {@code name} at THIS level, or null to fall back. */
    FieldTypeInfo field(String name);

    /**
     * @param typeLabel        what the "Type" column shows (e.g. "List&lt;Category&gt;")
     * @param structural       true to hide the field (plumbing/provenance)
     * @param nestedClassName  the referenced class's display name, for the expand
     *                         dialog caption (or null — falls back to the sample class)
     * @param nested           the source for the referenced object's fields (or null)
     */
    record FieldTypeInfo(String typeLabel, boolean structural,
                         String nestedClassName, FieldTypeSource nested) {}
}
