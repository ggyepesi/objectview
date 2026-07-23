package objectview.viewconfig;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.DynamicFields;
import objectview.field.FieldKind;
import objectview.field.ViewableFieldPaths;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
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

        // Schema-backed enumeration: no live sample, but an authoritative field-type
        // source that can LIST its fields (e.g. an empty reference in a compiled model).
        // This is what lets a valueless reference still expand into its children.
        if (context.sample() == null
                && context.fieldTypes() != null
                && !context.fieldTypes().fieldNames().isEmpty()) {
            addSchemaRows(result, context);
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
            NestedFieldSource nested = nestedFor(info, child);

            String typeLabel = info != null
                    ? info.typeLabel()
                    : dynamicTypeLabel(value, child);

            result.add(FieldRow.dynamic(name, typeLabel, nested));
        }
    }

    /** Enumerates a reference that has NO live sample value from its schema — the
     *  {@link FieldTypeSource#fieldNames()} of the nested source name the child fields.
     *  Mirrors {@link #addDynamicRows} but driven by the model, not a map. */
    private void addSchemaRows(List<FieldRow> result,
                               FieldRowContext context) {
        FieldTypeSource types = context.fieldTypes();

        if (!context.hiddenFields().contains("name")
                && !types.fieldNames().contains("name")) {
            result.add(FieldRow.dynamic("name", "String", null));
        }

        for (String name : types.fieldNames()) {
            if (context.hiddenFields().contains(name)) {
                continue;
            }
            FieldTypeSource.FieldTypeInfo info = types.field(name);
            if (info != null && info.structural()) {
                continue;
            }
            result.add(FieldRow.dynamic(
                    name,
                    info != null ? info.typeLabel() : "",
                    nestedFor(info, null)));
        }
    }

    /** The nested (expandable) source for a field, or null. A live child with fields
     *  wins; failing that, a schema that DECLARES a nested reference makes the field
     *  expandable even with no value — its children then come from the schema. */
    private static NestedFieldSource nestedFor(
            FieldTypeSource.FieldTypeInfo info, Viewable child) {
        boolean valueBacked = child != null && hasFields(child)
                && (info == null || info.nested() != null);
        if (valueBacked) {
            return new NestedFieldSource(
                    asViewableClass(child.getClass()),
                    child,
                    info == null ? null : info.nested(),
                    info == null ? null : info.nestedClassName());
        }
        if (info != null && info.nested() != null) {
            // No Java class for a model type -> a generic holder; it's never reflected
            // (the nested level takes the schema path above).
            return new NestedFieldSource(
                    child != null ? asViewableClass(child.getClass()) : Viewable.class,
                    child,
                    info.nested(),
                    info.nestedClassName());
        }
        return null;
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

        // The generic ARGUMENTS are shown for their own sake (a String element is as
        // informative as a String field) — independent of Viewable-ness, which only
        // governs whether the element is EXPANDABLE (handled by nestedViewableClass).
        if (Collection.class.isAssignableFrom(type)) {
            String elem = typeArg(field, 0);
            return elem == null ? "Collection" : "Collection<" + elem + ">";
        }

        if (Map.class.isAssignableFrom(type)) {
            String key = typeArg(field, 0);
            String value = typeArg(field, 1);
            return key == null && value == null
                    ? "Map"
                    : "Map<" + orWildcard(key) + ", " + orWildcard(value) + ">";
        }

        return type.getSimpleName();
    }

    /** The display name of {@code field}'s {@code index}-th generic type argument, or
     *  null when the field is raw (no parameters at that position). */
    private static String typeArg(Field field, int index) {
        if (field.getGenericType() instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (index < args.length) {
                return typeName(args[index]);
            }
        }
        return null;
    }

    private static String orWildcard(String name) {
        return name == null ? "?" : name;
    }

    /** Renders any reflected {@link Type} for the display label: a class by its simple
     *  name, a parameterized type recursively (e.g. {@code Map<String, Foo>}), a
     *  wildcard as {@code ?} (with bounds), a type variable by its name, arrays as
     *  {@code X[]}. */
    private static String typeName(Type type) {
        if (type instanceof Class<?> c) {
            return c.isArray()
                    ? typeName(c.getComponentType()) + "[]"
                    : c.getSimpleName();
        }
        if (type instanceof ParameterizedType pt) {
            StringBuilder sb = new StringBuilder(typeName(pt.getRawType()));
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0) {
                sb.append('<');
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(typeName(args[i]));
                }
                sb.append('>');
            }
            return sb.toString();
        }
        if (type instanceof WildcardType w) {
            if (w.getUpperBounds().length > 0
                    && w.getUpperBounds()[0] != Object.class) {
                return "? extends " + typeName(w.getUpperBounds()[0]);
            }
            if (w.getLowerBounds().length > 0) {
                return "? super " + typeName(w.getLowerBounds()[0]);
            }
            return "?";
        }
        if (type instanceof TypeVariable<?> tv) {
            return tv.getName();
        }
        if (type instanceof GenericArrayType g) {
            return typeName(g.getGenericComponentType()) + "[]";
        }
        return type.getTypeName();
    }
}
