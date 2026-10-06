package objectview.render;

import objectview.Viewable;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSchema;
import objectview.field.FieldSet;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.ConfiguredFieldSelection;

import java.util.Collection;
import java.util.Map;

/**
 * The one semantic decision for rendering an occurrence of a nested object.
 *
 * <p>Caption and body are deliberately independent. A selected DISPLAY field is
 * painted once, as the caption; it therefore does not count as expandable body
 * content. It never gates the object, its siblings, cycle handling or navigation.
 */
final class ObjectOccurrencePlan {

    record Display(String fieldName, String value) {}

    private final Display display;
    private final boolean selectedBody;

    private ObjectOccurrencePlan(Display display, boolean selectedBody) {
        this.display = display;
        this.selectedBody = selectedBody;
    }

    static ObjectOccurrencePlan of(
            Viewable target, ViewConfig config, FieldSchema schema) {
        FieldSet fields = FieldSet.of(target, schema);
        Display display = selectedDisplay(fields, config);
        boolean body = false;
        for (FieldRef field : fields.fields()) {
            if (field.role() == FieldRole.IDENTITY
                    || field.role() == FieldRole.DISPLAY
                    || !selected(config, field)) continue;
            Object value = fields.read(field.name());
            if (value instanceof Collection<?> || value instanceof Map<?, ?>
                    || hasContent(value)) {
                body = true;
                break;
            }
        }
        return new ObjectOccurrencePlan(display, body);
    }

    Display display() { return display; }
    boolean hasSelectedBody() { return selectedBody; }

    static boolean selected(ViewConfig config, FieldRef field) {
        return ConfiguredFieldSelection.selected(config, field);
    }

    private static Display selectedDisplay(FieldSet fields, ViewConfig config) {
        if (fields == null || config == null) return null;
        FieldRef field = fields.displayField();
        if (field == null || !selected(config, field)) return null;
        Object value = fields.read(field.name());
        return hasContent(value)
                ? new Display(field.name(), String.valueOf(value)) : null;
    }

    private static boolean hasContent(Object value) {
        if (value == null) return false;
        if (value instanceof CharSequence text) return !text.toString().isBlank();
        if (value instanceof Collection<?> values) return !values.isEmpty();
        if (value instanceof Map<?, ?> values) return !values.isEmpty();
        return true;
    }
}
