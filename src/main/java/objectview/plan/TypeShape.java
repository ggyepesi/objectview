package objectview.plan;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSchema;
import objectview.field.FieldSet;
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

    /** A domain schema, as the field editors receive it. */
    static TypeShape of(FieldTypeSource source) {
        return source == null ? null : new SchemaShape(source);
    }

    /** A reflected class: its declared fields plus the contract fields. */
    static TypeShape ofClass(Class<? extends Viewable> type) {
        return type == null ? null : new ClassShape(type);
    }

    /** A sample instance read through its schema; nested shapes come from the sample's
     * own values. */
    static TypeShape ofSample(Viewable sample, Function<Viewable, FieldSchema> schemas) {
        return sample == null ? null : new SampleShape(sample, schemas);
    }

    record SchemaShape(FieldTypeSource source) implements TypeShape {
        @Override public List<FieldRef> fields() {
            List<FieldRef> out = new ArrayList<>();
            for (String name : source.fieldNames()) {
                FieldTypeSource.FieldTypeInfo info = source.field(name);
                if (info == null) continue;
                FieldKind kind = info.kind() == null ? FieldKind.UNKNOWN : info.kind();
                FieldKind valueKind = info.valueKind() == null ? kind : info.valueKind();
                out.add(FieldRef.described(name, info.label(), info.role(),
                        kind, valueKind, info.typeLabel(), info.nested() != null,
                        kind == FieldKind.COLLECTION, info.nestedClassName(),
                        info.structural(), info.minor(),
                        false, false, false, "", false));
            }
            return out;
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

    record SampleShape(Viewable sample, Function<Viewable, FieldSchema> schemas)
            implements TypeShape {
        private FieldSet set() {
            return FieldSet.of(sample, schemas == null ? null : schemas.apply(sample));
        }

        @Override public List<FieldRef> fields() { return set().fields(); }

        @Override public Field javaField(FieldRef field) {
            return field == null ? null : ViewableAdapter.getField(sample.getClass(), field.name());
        }

        @Override public TypeShape nested(FieldRef field) {
            if (field == null) return null;
            Object value = set().read(field.name());
            Viewable target = firstViewable(value);
            return target == null ? null : TypeShape.ofSample(target, schemas);
        }

        private static Viewable firstViewable(Object value) {
            if (value instanceof Viewable viewable) return viewable;
            Iterable<?> items = value instanceof Collection<?> collection ? collection
                    : value instanceof Map<?, ?> map ? map.values() : List.of();
            for (Object item : items) {
                if (item instanceof Viewable viewable) return viewable;
            }
            return null;
        }
    }
}
