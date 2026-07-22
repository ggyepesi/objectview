package objectview.viewconfig;

import objectview.Viewable;

/**
 * Immutable description of a row that can open a nested field editor.
 *
 * <p>Keeping these values together prevents invalid combinations of a nested class,
 * runtime sample, authoritative dynamic type source and display label.
 */
public record NestedFieldSource(
        Class<? extends Viewable> type,
        Viewable sample,
        FieldTypeSource fieldTypes,
        String displayName) {

    public NestedFieldSource {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }

    public String effectiveDisplayName() {
        return displayName == null || displayName.isBlank()
                ? type.getSimpleName()
                : displayName;
    }
}
