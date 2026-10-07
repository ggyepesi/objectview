package objectview.plan;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSchema;
import objectview.field.FieldSet;
import objectview.field.RecordTypes;
import objectview.field.ReflectionFieldSet;
import objectview.field.ViewableContractFieldSet;
import objectview.viewconfig.FieldTypeSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The fields of one logical type as a config sees them, and the shape each object
 * field leads to. It is what {@link ViewConfigDesugar} expands shorthand against, so
 * it is read when a config enters, never while painting.
 *
 * <p>Built from the descriptions that already exist: a domain {@link FieldTypeSource},
 * a reflected class, or a sample instance with its schema.
 */
public interface TypeShape {

    /** The fields at this level, in declaration order. */
    List<FieldRef> fields();

    /** The shape an object-valued field leads to, or null when the field is not an
     * object field or its target is not described. */
    TypeShape nested(FieldRef field);

    /** The declared Java field behind {@code field}, or null for a dynamic or
     * contract field. Sort reads a numeric Java type off it. */
    default Field javaField(FieldRef field) { return null; }

    /** The field carrying the DISPLAY role at this level, or null. */
    default FieldRef displayField() {
        for (FieldRef field : fields()) {
            if (field.role() == FieldRole.DISPLAY) return field;
        }
        return null;
    }

    /** {@code fields} (first of each name kept) with the contract fields when no real
     * field carries DISPLAY — the one rule every shape follows, as an object's field set
     * does. */
    private static List<FieldRef> withContractFields(List<FieldRef> fields) {
        Map<String, FieldRef> out = new java.util.LinkedHashMap<>();
        for (FieldRef field : fields) out.putIfAbsent(field.name(), field);
        boolean display = out.values().stream().anyMatch(field ->
                field.role() == FieldRole.DISPLAY
                        && !field.name().equals(ViewableContractFieldSet.DISPLAY_KEY));
        if (display) out.remove(ViewableContractFieldSet.DISPLAY_KEY);
        else for (FieldRef contract : ViewableContractFieldSet.fieldRefs()) {
            out.putIfAbsent(contract.name(), contract);
        }
        return new ArrayList<>(out.values());
    }

    /** {@code shape} as the field editors read a domain schema: one row per field,
     * each object field expanding into its declared target type's shape. */
    static FieldTypeSource fieldTypes(TypeShape shape) {
        if (shape == null) return null;
        return new FieldTypeSource() {
            @Override public FieldTypeInfo field(String name) {
                for (FieldRef field : shape.fields()) {
                    if (!field.name().equals(name)) continue;
                    TypeShape nested = shape.nested(field);
                    String target = field.targetType() != null && !field.targetType().isBlank()
                            ? field.targetType() : field.typeLabel();
                    return new FieldTypeInfo(field.typeLabel(), field.structural(),
                            field.minor(), nested == null ? null : target,
                            fieldTypes(nested), field.label(), field.role(),
                            field.kind(), field.valueKind(), field.embedded());
                }
                return null;
            }

            @Override public List<String> fieldNames() {
                return shape.fields().stream().map(FieldRef::name).toList();
            }
        };
    }

    /** A domain schema, as the field editors receive it. */
    static TypeShape of(FieldTypeSource source) {
        return source == null ? null : new SchemaShape(source);
    }

    /** A reflected class: its declared fields plus the contract fields. */
    static TypeShape ofClass(Class<? extends Viewable> type) {
        return type == null ? null : new ClassShape(type);
    }

    /**
     * The type of {@code value}: its schema ({@code schema}, else the one a dynamic
     * value carries) together with what its Java class declares — never the values it
     * holds. A nested shape is the field's declared target type, looked up in
     * {@code byType}; with no schema the value's class is reflected.
     */
    static TypeShape of(Viewable value, FieldSchema schema,
                        Function<String, FieldSchema> byType) {
        if (value == null) return null;
        FieldSchema effective = schema != null ? schema : FieldSet.carriedSchema(value);
        return effective == null ? ofClass(value.getClass())
                : new SchemaTypeShape(effective, value.getClass(),
                        !FieldSet.declaresItsFields(value), byType);
    }

    /** The declared type {@code typeName}, from its schema (a domain's, else a runtime
     * record type's declaration); null when it has none. */
    static TypeShape ofType(String typeName, Function<String, FieldSchema> byType) {
        if (typeName == null) return null;
        FieldSchema schema = byType == null ? null : byType.apply(typeName);
        if (schema == null) schema = RecordTypes.schema(typeName);
        return schema == null ? null : new SchemaTypeShape(schema, null, false, byType);
    }

    record SchemaShape(FieldTypeSource source) implements TypeShape {
        @Override public List<FieldRef> fields() {
            List<FieldRef> out = new ArrayList<>();
            for (String name : source.fieldNames()) {
                FieldTypeSource.FieldTypeInfo info = source.field(name);
                if (info == null) continue;
                FieldKind kind = info.kind() == null ? FieldKind.UNKNOWN : info.kind();
                FieldKind valueKind = info.valueKind() == null ? kind : info.valueKind();
                // A reference by its kind, not by whether its target declares fields: a
                // vocabulary target (Type, a given name) has none and is still an object.
                boolean reference = info.nested() != null || kind == FieldKind.REFERENCE
                        || valueKind == FieldKind.REFERENCE;
                out.add(FieldRef.described(name, info.label(), info.role(),
                        kind, valueKind, info.typeLabel(), reference,
                        kind == FieldKind.COLLECTION, info.nestedClassName(),
                        info.structural(), info.minor(),
                        info.embedded(), info.embedded(),
                        false, "", false));
            }
            return withContractFields(out);
        }

        @Override public TypeShape nested(FieldRef field) {
            FieldTypeSource.FieldTypeInfo info = field == null ? null : source.field(field.name());
            return info == null ? null : TypeShape.of(info.nested());
        }
    }

    record ClassShape(Class<? extends Viewable> type) implements TypeShape {
        @Override public List<FieldRef> fields() {
            List<FieldRef> out = new ArrayList<>();
            boolean hasDisplay = false;
            for (Field field : ViewableAdapter.getAllFields(type)) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                FieldRef ref = ReflectionFieldSet.describe(field, type);
                hasDisplay |= ref.role() == FieldRole.DISPLAY;
                out.add(ref);
            }
            for (FieldRef contract : ViewableContractFieldSet.fieldRefs()) {
                if (contract.role() == FieldRole.DISPLAY && hasDisplay) continue;
                out.add(contract);
            }
            return out;
        }

        @Override public Field javaField(FieldRef ref) {
            return ref == null ? null : ViewableAdapter.getField(type, ref.name());
        }

        @Override public TypeShape nested(FieldRef ref) {
            Field field = ref == null ? null : ViewableAdapter.getField(type, ref.name());
            Class<? extends Viewable> target = field == null ? null : viewableTarget(field);
            return TypeShape.ofClass(target);
        }

        @SuppressWarnings("unchecked")
        private static Class<? extends Viewable> viewableTarget(Field field) {
            Class<?> raw = field.getType();
            if (Viewable.class.isAssignableFrom(raw)) return (Class<? extends Viewable>) raw;
            if (!Collection.class.isAssignableFrom(raw) && !Map.class.isAssignableFrom(raw)) {
                return null;
            }
            if (!(field.getGenericType() instanceof ParameterizedType generic)) return null;
            Type[] arguments = generic.getActualTypeArguments();
            Type element = arguments[arguments.length - 1];
            return element instanceof Class<?> cls && Viewable.class.isAssignableFrom(cls)
                    ? (Class<? extends Viewable>) cls : null;
        }
    }

    /**
     * A schema's fields, with the role and link fields its Java class declares (for a
     * dynamic value, whose other class fields are storage) and the contract fields when
     * nothing carries DISPLAY. A field the schema declares nests into its target type's
     * schema; a field only the class declares nests through the class.
     */
    record SchemaTypeShape(FieldSchema schema, Class<?> declaring, boolean dynamic,
                           Function<String, FieldSchema> byType) implements TypeShape {
        @Override public List<FieldRef> fields() {
            List<FieldRef> out = new ArrayList<>(schema.fields());
            if (declaring != null && Viewable.class.isAssignableFrom(declaring)) {
                for (FieldRef field : classShape().fields()) {
                    if (dynamic && !(field.link() || field.role() != FieldRole.NONE)) continue;
                    out.add(field);
                }
            }
            return withContractFields(out);
        }

        @Override public Field javaField(FieldRef field) {
            return field == null || declaring == null
                    ? null : ViewableAdapter.getField(declaring, field.name());
        }

        @Override public TypeShape nested(FieldRef field) {
            if (field == null) return null;
            if (schema.field(field.name()) != null) {
                return TypeShape.ofType(field.targetType(), byType);
            }
            return declaring == null ? null : classShape().nested(field);
        }

        @SuppressWarnings("unchecked")
        private ClassShape classShape() {
            return new ClassShape((Class<? extends Viewable>) declaring);
        }
    }
}
