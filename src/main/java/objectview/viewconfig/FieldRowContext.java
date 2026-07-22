package objectview.viewconfig;

import objectview.Viewable;

import java.util.Set;

/**
 * Immutable input used by {@link FieldRowSource}.
 */
public record FieldRowContext(
        ViewConfig config,
        Viewable sample,
        boolean minorOnly,
        boolean hideMedia,
        Set<String> hiddenFields,
        FieldTypeSource fieldTypes) {

    public FieldRowContext {
        config = config == null ? new ViewConfig() : config;
        hiddenFields = hiddenFields == null ? Set.of() : Set.copyOf(hiddenFields);
    }
}
