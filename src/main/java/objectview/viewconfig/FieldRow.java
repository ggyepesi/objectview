package objectview.viewconfig;

import objectview.ViewableAdapter;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Immutable description of one row in a field table.
 *
 * <p>Mutable editor state — whether the row is checked and which nested editor is
 * open — lives in {@link ViewConfigEditor}, not in this model.
 */
public final class FieldRow {

    public enum Kind {
        FIELD,
        CONTAINER,
        MINOR_BLOCK
    }

    private final Kind kind;
    private final String path;
    private final String label;
    private final String typeLabel;
    private final int depth;
    private final Field field;
    private final NestedFieldSource nested;

    private FieldRow(Kind kind,
                     String path,
                     String label,
                     String typeLabel,
                     int depth,
                     Field field,
                     NestedFieldSource nested) {
        this.kind = Objects.requireNonNull(kind);
        this.path = Objects.requireNonNull(path);
        this.label = Objects.requireNonNull(label);
        this.typeLabel = typeLabel == null ? "" : typeLabel;
        this.depth = depth;
        this.field = field;
        this.nested = nested;
    }

    public static FieldRow reflected(Field field,
                                     String typeLabel,
                                     NestedFieldSource nested) {
        return new FieldRow(
                Kind.FIELD,
                field.getName(),
                field.getName(),
                typeLabel,
                0,
                field,
                nested);
    }

    public static FieldRow dynamic(String name,
                                   String typeLabel,
                                   NestedFieldSource nested) {
        return new FieldRow(
                Kind.FIELD,
                name,
                name,
                typeLabel,
                0,
                null,
                nested);
    }

    public static FieldRow path(String label,
                                String path,
                                int depth,
                                boolean container,
                                String typeLabel) {
        return new FieldRow(
                container ? Kind.CONTAINER : Kind.FIELD,
                path,
                label,
                typeLabel,
                depth,
                null,
                null);
    }

    public static FieldRow minorBlock() {
        return new FieldRow(
                Kind.MINOR_BLOCK,
                "Minor fields",
                "Minor fields",
                "",
                0,
                null,
                null);
    }

    /**
     * This row placed under a parent for the inline tree: the same field metadata
     * (kind, label, type, backing {@link Field}, nested source) at a new full dotted
     * {@code path} and {@code depth}. A row source emits a child with its own short
     * name and depth 0; the editor reparents it as it expands.
     */
    public FieldRow at(String fullPath, int depth) {
        return new FieldRow(kind, fullPath, label, typeLabel, depth, field, nested);
    }

    public Kind kind() {
        return kind;
    }

    public String path() {
        return path;
    }

    public String label() {
        return label;
    }

    public String typeLabel() {
        return typeLabel;
    }

    public int depth() {
        return depth;
    }

    public Field field() {
        return field;
    }

    public NestedFieldSource nested() {
        return nested;
    }

    public boolean isField() {
        return kind == Kind.FIELD;
    }

    public boolean isContainer() {
        return kind == Kind.CONTAINER;
    }

    public boolean isMinorBlock() {
        return kind == Kind.MINOR_BLOCK;
    }

    public boolean isSpecial() {
        return kind != Kind.FIELD;
    }

    public boolean isMinor() {
        return field != null && ViewableAdapter.isMinorField(field);
    }

    public String indentedLabel() {
        return depth <= 0 ? label : "    ".repeat(depth) + label;
    }

    @Override
    public String toString() {
        return path;
    }
}
