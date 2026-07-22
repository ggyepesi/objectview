package objectview.viewconfig;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.DynamicFields;
import objectview.field.FieldKind;
import objectview.field.ViewableFieldPaths;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Default row source for reflected Viewable classes and map-backed dynamic samples.
 */
public final class ConfigFieldRowSource implements FieldRowSource {

    public static final ConfigFieldRowSource INSTANCE =
            new ConfigFieldRowSource();

    private ConfigFieldRowSource() {
    }

    @Override
    public List<FieldRow> rows(FieldRowContext context) {
        List<FieldRow> result = new ArrayList<>();

        if (context.sample() instanceof DynamicFields) {
            addDynamicRows(result, context);
            return List.copyOf(result);
        }

        Class<? extends Viewable> cls = context.config().getCls();
        if (cls == null) {
            return List.of();
        }

        if (context.minorOnly()) {
            addReflectedRows(result, context, cls, true);
        } else {
            addReflectedRows(result, context, cls, false);
            if (hasMinorFields(cls)) {
                result.add(FieldRow.minorBlock());
            }
        }

        return List.copyOf(result);
    }

    private void addDynamicRows(List<FieldRow> result,
                                FieldRowContext context) {
        DynamicFields dynamic = (DynamicFields) context.sample();

        // `name` is identity/display data rather than a map entry, but config/search
        // editors must be able to include or exclude it like any other field.
        if (!context.hiddenFields().contains("name")
                && !dynamic.dynamicFieldValues().containsKey("name")) {
            result.add(FieldRow.dynamic("name", "String", null));
        }

        for (Map.Entry<String, Object> entry
                : dynamic.dynamicFieldValues().entrySet()) {
            String name = entry.getKey();
            FieldTypeSource.FieldTypeInfo info =
                    context.fieldTypes() == null
                            ? null
                            : context.fieldTypes().field(name);

            // Structural model fields are hidden just like explicit hiddenFields.
            if (context.hiddenFields().contains(name)
                    || (info != null && info.structural())) {
                continue;
            }

            Object value = entry.getValue();
            if (context.hideMedia()
                    && FieldKind.ofValue(value) == FieldKind.MEDIA) {
                continue;
            }

            Viewable child = firstViewable(value);
            boolean modelExpandable =
                    info == null || info.nested() != null;

            Class<? extends Viewable> nestedClass =
                    modelExpandable && child != null && hasFields(child)
                            ? asViewableClass(child.getClass())
                            : null;

            NestedFieldSource nested = nestedClass == null
                    ? null
                    : new NestedFieldSource(
                            nestedClass,
                            child,
                            info == null ? null : info.nested(),
                            info == null ? null : info.nestedClassName());

            String typeLabel = info != null
                    ? info.typeLabel()
                    : dynamicTypeLabel(value, child);

            result.add(FieldRow.dynamic(name, typeLabel, nested));
        }
    }

    private void addReflectedRows(List<FieldRow> result,
                                  FieldRowContext context,
                                  Class<? extends Viewable> cls,
                                  boolean minor) {
        for (Field field : ViewableAdapter.getConfigurableFields(cls)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (ViewableAdapter.isMinorField(field) != minor) {
                continue;
            }
            if (context.hiddenFields().contains(field.getName())) {
                continue;
            }
            if (context.hideMedia()
                    && FieldKind.ofClass(field.getType()) == FieldKind.MEDIA) {
                continue;
            }

            Class<? extends Viewable> nestedClass =
                    ViewableFieldPaths.nestedViewableClass(field);

            NestedFieldSource nested = nestedClass == null
                    ? null
                    : new NestedFieldSource(
                            nestedClass,
                            null,
                            null,
                            nestedClass.getSimpleName());

            result.add(FieldRow.reflected(
                    field,
                    describeFieldType(field),
                    nested));
        }
    }

    private static boolean hasMinorFields(
            Class<? extends Viewable> cls) {
        for (Field field : ViewableAdapter.getAllFields(cls)) {
            if (!Modifier.isStatic(field.getModifiers())
                    && ViewableAdapter.isMinorField(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFields(Viewable viewable) {
        if (viewable instanceof DynamicFields dynamic) {
            return !dynamic.dynamicFieldValues().isEmpty();
        }
        return !ViewableAdapter.getAllFields(
                viewable.getClass()).isEmpty();
    }

    private static Viewable firstViewable(Object value) {
        if (value instanceof Viewable viewable) {
            return viewable;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Viewable viewable) {
                    return viewable;
                }
            }
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (item instanceof Viewable viewable) {
                    return viewable;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Viewable> asViewableClass(
            Class<?> cls) {
        return (Class<? extends Viewable>) cls;
    }

    private static String dynamicTypeLabel(
            Object value,
            Viewable child) {
        if (child != null) {
            return value instanceof Collection<?>
                    ? "Collection<" + child.typeName() + ">"
                    : child.typeName();
        }
        if (value instanceof Collection<?>) {
            return "Collection";
        }
        return value == null
                ? ""
                : value.getClass().getSimpleName();
    }

    private static String describeFieldType(Field field) {
        Class<?> type = field.getType();

        if (Viewable.class.isAssignableFrom(type)) {
            return type.getSimpleName();
        }

        if (Collection.class.isAssignableFrom(type)) {
            Class<? extends Viewable> nested =
                    ViewableFieldPaths.nestedViewableClass(field);
            return nested == null
                    ? "Collection"
                    : "Collection<" + nested.getSimpleName() + ">";
        }

        if (Map.class.isAssignableFrom(type)) {
            Class<? extends Viewable> nested =
                    ViewableFieldPaths.nestedViewableClass(field);
            return nested == null
                    ? "Map"
                    : "Map<?, " + nested.getSimpleName() + ">";
        }

        return type.getSimpleName();
    }
}
