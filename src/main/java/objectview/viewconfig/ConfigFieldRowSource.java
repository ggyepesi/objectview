package objectview.viewconfig;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.FieldRef;
import objectview.field.FieldSet;
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

        // Schema-backed enumeration: no live sample, but an authoritative field-type
        // source that can LIST its fields (e.g. an empty reference in a compiled model).
        // This is what lets a valueless reference still expand into its children.
        if (context.sample() == null
                && context.fieldTypes() != null
                && !context.fieldTypes().fieldNames().isEmpty()) {
            addSchemaRows(result, context);
            return List.copyOf(result);
        }

        if (context.sample() == null) {
            // No instance and no schema: describe the configured CLASS by reflection.
            // FieldSet.of() needs a live object, so a class-only config table (a
            // ViewConfig with a getCls() but no sample yet) enumerates here instead.
            Class<? extends Viewable> cls = context.config().getCls();
            if (cls != null) {
                addReflectedClassRows(result, context, cls);
            }
            return List.copyOf(result);
        }
        addFieldSetRows(result, context);
        return List.copyOf(result);
    }

    /** Enumerates a class's configurable fields with NO instance — the type label comes
     *  from reflection ({@link #describeFieldType}). The instance-based {@code FieldSet}
     *  path cannot run without a sample, so a class-only config table uses this. */
    private void addReflectedClassRows(List<FieldRow> result,
                                       FieldRowContext context,
                                       Class<? extends Viewable> cls) {
        List<Field> reflectedFields = ViewableAdapter.getAllFields(cls);
        if (!context.minorOnly()) {
            boolean hasDisplayField = !objectview.field.ViewableContractFieldSet.DISPLAY_KEY
                    .equals(objectview.field.ViewableContractFieldSet.displayKey(cls));
            for (FieldRef field : hasDisplayField
                    ? List.<FieldRef>of()
                    : objectview.field.ViewableContractFieldSet.fieldRefs()) {
                boolean physicallyDeclared = reflectedFields.stream()
                        .anyMatch(candidate -> candidate.getName().equals(field.name()));
                if (!physicallyDeclared && !context.hiddenFields().contains(field.name())) {
                    result.add(FieldRow.dynamic(
                            field.name(), field.label(), field.typeLabel(), null));
                }
            }
        }
        for (Field field : reflectedFields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            if (context.hiddenFields().contains(name)
                    || ViewableAdapter.isMinorField(field) != context.minorOnly()) {
                continue;
            }
            if (context.hideMedia()
                    && FieldKind.ofClass(field.getType()) == FieldKind.MEDIA) {
                continue;
            }
            Class<? extends Viewable> nestedClass =
                    ViewableFieldPaths.nestedViewableClass(field);
            NestedFieldSource nested = nestedClass == null ? null
                    : new NestedFieldSource(
                            nestedClass, null, null, nestedClass.getSimpleName());
            result.add(FieldRow.reflected(
                    field, describeFieldType(field, cls), nested));
        }
    }

    /** One enumeration path for reflected and dynamic objects. Storage selection and
     * annotation/schema overlay happen inside FieldSet.of(). */
    private void addFieldSetRows(
            List<FieldRow> result, FieldRowContext context) {
        Viewable sample = context.sample();
        FieldSet fields = FieldSet.of(sample);
        for (FieldRef field : fields.fields()) {
            String name = field.name();
            FieldTypeSource.FieldTypeInfo info = context.fieldTypes() == null
                    ? null : context.fieldTypes().field(name);
            boolean minor = field.minor() || info != null && info.minor();
            if (context.hiddenFields().contains(name)
                    || field.structural()
                    || info != null && info.structural()
                    || minor != context.minorOnly()) {
                continue;
            }
            if (context.hideMedia() && field.valueKind() == FieldKind.MEDIA) {
                continue;
            }

            Object value = fields.read(name);
            Viewable child = firstViewable(value);
            NestedFieldSource nested = nestedFor(info, child);
            Field reflected = ViewableAdapter.getField(sample.getClass(), name);
            if (nested == null && reflected != null) {
                Class<? extends Viewable> nestedClass =
                        ViewableFieldPaths.nestedViewableClass(reflected);
                if (nestedClass != null) {
                    nested = new NestedFieldSource(
                            nestedClass, child, null, nestedClass.getSimpleName());
                }
            }
            String label = info != null && info.typeLabel() != null
                    ? info.typeLabel() : field.typeLabel();
            String displayLabel = info != null && info.label() != null
                    ? info.label() : field.label();
            result.add(reflected == null
                    ? FieldRow.dynamic(name, displayLabel, label, nested)
                    : FieldRow.reflected(reflected, label, nested));
        }
    }

    /** Enumerates a reference that has NO live sample value from its schema — the
     *  {@link FieldTypeSource#fieldNames()} of the nested source name the child fields.
     *  Driven by the model rather than a live value. */
    private void addSchemaRows(List<FieldRow> result,
                               FieldRowContext context) {
        FieldTypeSource types = context.fieldTypes();

        if (!context.minorOnly()) {
            // More than one DISPLAY field is a small user error, not a crash (last wins):
            // as long as at least one real field is bound, skip the synthetic display field.
            boolean hasDisplayField = types.fieldNames().stream().anyMatch(name -> {
                FieldTypeSource.FieldTypeInfo info = types.field(name);
                return info != null && info.role() == objectview.field.FieldRole.DISPLAY;
            });
            for (FieldRef field : hasDisplayField
                    ? List.<FieldRef>of()
                    : objectview.field.ViewableContractFieldSet.fieldRefs()) {
                if (!context.hiddenFields().contains(field.name())
                        && !types.fieldNames().contains(field.name())) {
                    result.add(FieldRow.dynamic(
                            field.name(), field.label(), field.typeLabel(), null));
                }
            }
        }

        for (String name : types.fieldNames()) {
            if (context.hiddenFields().contains(name)) {
                continue;
            }
            FieldTypeSource.FieldTypeInfo info = types.field(name);
            if (info != null && info.structural()) {
                continue;
            }
            boolean minor = info != null && info.minor();
            if (minor != context.minorOnly()) {
                continue;
            }
            result.add(FieldRow.dynamic(
                    name,
                    info != null && info.label() != null ? info.label() : name,
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

    /**
     * Whether {@code context} has any minor field to segregate — reflected (annotation),
     * dynamic sample, or schema-only reference alike. Lets the editor show the
     * "All minor fields" bar for a dynamic/snapshot type, not just a reflected class.
     */
    public boolean hasMinorFields(FieldRowContext context) {
        if (context.sample() == null
                && context.fieldTypes() != null
                && !context.fieldTypes().fieldNames().isEmpty()) {
            return anyMinor(context.fieldTypes().fieldNames(), context);
        }
        if (context.sample() == null) {
            return false;
        }
        for (FieldRef field : FieldSet.of(context.sample()).fields()) {
            FieldTypeSource.FieldTypeInfo info = context.fieldTypes() == null
                    ? null : context.fieldTypes().field(field.name());
            if (!context.hiddenFields().contains(field.name())
                    && !field.structural()
                    && (field.minor() || info != null && info.minor())) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyMinor(Collection<String> names,
                                    FieldRowContext context) {
        FieldTypeSource types = context.fieldTypes();
        if (types == null) {
            return false;
        }
        for (String name : names) {
            if (context.hiddenFields().contains(name)) {
                continue;
            }
            FieldTypeSource.FieldTypeInfo info = types.field(name);
            if (info != null && info.minor() && !info.structural()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFields(Viewable viewable) {
        return !FieldSet.of(viewable).fields().isEmpty();
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

    /** Describes a reflected field with the same generic-type resolution used by
     *  the config editor. Exposed so persistence bridges can save the declared
     *  schema even when a field currently has no value. */
    public static String describeFieldType(Field field, Class<?> owner) {
        Class<?> type = field.getType();

        if (Viewable.class.isAssignableFrom(type)) {
            return type.getSimpleName();
        }

        // Resolve any type VARIABLES (e.g. G/T of a generic superclass) to the owner's
        // actual arguments, so e.g. DefaultViewableGroup's Map<String, G> shows as
        // Map<String, ViewableGroup> rather than Map<String, G>.
        Map<TypeVariable<?>, Type> bindings = typeBindings(owner);

        // The generic ARGUMENTS are shown for their own sake (a String element is as
        // informative as a String field) — independent of Viewable-ness, which only
        // governs whether the element is EXPANDABLE (handled by nestedViewableClass).
        if (Collection.class.isAssignableFrom(type)) {
            String elem = typeArg(field, 0, bindings);
            return elem == null ? "Collection" : "Collection<" + elem + ">";
        }

        if (Map.class.isAssignableFrom(type)) {
            String key = typeArg(field, 0, bindings);
            String value = typeArg(field, 1, bindings);
            return key == null && value == null
                    ? "Map"
                    : "Map<" + orWildcard(key) + ", " + orWildcard(value) + ">";
        }

        return type.getSimpleName();
    }

    /** The display name of {@code field}'s {@code index}-th generic type argument, or
     *  null when the field is raw (no parameters at that position). */
    private static String typeArg(Field field, int index,
                                  Map<TypeVariable<?>, Type> bindings) {
        if (field.getGenericType() instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (index < args.length) {
                return typeName(args[index], bindings);
            }
        }
        return null;
    }

    private static String orWildcard(String name) {
        return name == null ? "?" : name;
    }

    /** Renders any reflected {@link Type} for the display label: a class by its simple
     *  name, a parameterized type recursively (e.g. {@code Map<String, Foo>}), a
     *  wildcard as {@code ?} (with bounds), a type variable resolved via {@code
     *  bindings} (else its bare name), arrays as {@code X[]}. */
    private static String typeName(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> tv) {
            Type bound = bindings.get(tv);
            return bound != null && bound != tv
                    ? typeName(bound, bindings)
                    : tv.getName();
        }
        if (type instanceof Class<?> c) {
            return c.isArray()
                    ? typeName(c.getComponentType(), bindings) + "[]"
                    : c.getSimpleName();
        }
        if (type instanceof ParameterizedType pt) {
            StringBuilder sb = new StringBuilder(typeName(pt.getRawType(), bindings));
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0) {
                sb.append('<');
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(typeName(args[i], bindings));
                }
                sb.append('>');
            }
            return sb.toString();
        }
        if (type instanceof WildcardType w) {
            if (w.getUpperBounds().length > 0
                    && w.getUpperBounds()[0] != Object.class) {
                return "? extends " + typeName(w.getUpperBounds()[0], bindings);
            }
            if (w.getLowerBounds().length > 0) {
                return "? super " + typeName(w.getLowerBounds()[0], bindings);
            }
            return "?";
        }
        if (type instanceof GenericArrayType g) {
            return typeName(g.getGenericComponentType(), bindings) + "[]";
        }
        return type.getTypeName();
    }

    /** Maps each type variable declared by {@code owner}'s generic superclass chain to
     *  the concrete argument {@code owner} binds it to — so a field inherited from a
     *  generic base (e.g. {@code Map<String, G>}) can be shown with the real type. */
    private static Map<TypeVariable<?>, Type> typeBindings(Class<?> owner) {
        Map<TypeVariable<?>, Type> map = new java.util.HashMap<>();
        Type sup = owner == null ? null : owner.getGenericSuperclass();
        while (sup instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            TypeVariable<?>[] params = raw.getTypeParameters();
            Type[] args = pt.getActualTypeArguments();
            for (int i = 0; i < params.length && i < args.length; i++) {
                // Substitute already-known bindings (chained generics pass a var down).
                Type arg = args[i];
                if (arg instanceof TypeVariable<?> tv && map.containsKey(tv)) {
                    arg = map.get(tv);
                }
                map.put(params[i], arg);
            }
            sup = raw.getGenericSuperclass();
        }
        return map;
    }
}
