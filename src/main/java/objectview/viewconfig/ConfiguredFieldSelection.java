package objectview.viewconfig;

import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.ViewableContractFieldSet;

import java.util.Set;

/** The one literal ViewConfig selection rule shared by every renderer. */
public final class ConfiguredFieldSelection {
    private ConfiguredFieldSelection() {}

    public static boolean selected(
            FieldRef field,
            boolean allFields,
            boolean allMinorFields,
            Set<String> explicitlySelected) {
        if (field == null) return false;
        Set<String> explicit = explicitlySelected == null
                ? Set.of() : explicitlySelected;
        if (explicit.contains(field.name())) return true;
        if (field.role() == FieldRole.DISPLAY
                && explicit.contains(ViewableContractFieldSet.DISPLAY_KEY)) return true;
        return field.minor() ? allMinorFields : allFields;
    }

    public static boolean selected(ViewConfig config, FieldRef field) {
        return config != null && selected(
                field,
                config.isAllFields(),
                config.isAllMinorFields(),
                config.getFields().keySet());
    }
}
