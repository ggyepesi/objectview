package objectview.viewconfig;

import java.util.List;

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

    /** The field names this source can describe at THIS level — used to enumerate a
     *  schema-backed reference that has no live sample value (an empty reference).
     *  Empty by default: a source that only answers {@link #field} per-name still
     *  works whenever a sample IS present. */
    default List<String> fieldNames() {
        return List.of();
    }

    /**
     * @param typeLabel        what the "Type" column shows (e.g. "List&lt;Category&gt;")
     * @param structural       true to hide the field (schema plumbing)
     * @param minor            true to keep the field out of the ordinary table and
     *                         under "All minor fields", which governs it wholesale.
     *                         Unlike a reflected {@code @Minor} field it raises no
     *                         minor BLOCK row: the block stands for a minor set as a
     *                         configurable group and follows what a class declares,
     *                         so a schema-declared field answering to the checkbox
     *                         too would sit under two controls that can disagree.
     *                         See ConfigFieldRowSource.hasBlockGovernedMinorFields.
     * @param nestedClassName  the referenced class's display name, for the expand
     *                         dialog caption (or null — falls back to the sample class)
     * @param nested           the source for the referenced object's fields (or null)
     */
    record FieldTypeInfo(String typeLabel, boolean structural, boolean minor,
                         String nestedClassName, FieldTypeSource nested,
                         String label, objectview.field.FieldRole role,
                         objectview.field.FieldKind kind,
                         objectview.field.FieldKind valueKind) {

    }
}
