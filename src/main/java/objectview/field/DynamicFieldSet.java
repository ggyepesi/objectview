package objectview.field;

import objectview.Viewable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A {@link FieldSet} over a {@link DynamicFields} object's property map (the "new"
 * representation — e.g. {@code WikidataDynamicObject}). With a {@link FieldSchema}
 * the fields are typed authoritatively (cardinality and type survive null / single
 * values); without one, types are inferred from the present VALUES (a single-value
 * collection then reads as a scalar). See #87.
 */
public final class DynamicFieldSet implements FieldSet {

    private final DynamicFields object;
    private final FieldSchema schema;   // nullable — authoritative types when present

    public DynamicFieldSet(DynamicFields object) {
        this(object, null);
    }

    public DynamicFieldSet(DynamicFields object, FieldSchema schema) {
        this.object = object;
        this.schema = schema;
    }

    @Override
    public Object read(String name) {
        return object.dynamicFieldValues().get(name);
    }

    @Override
    public boolean has(String name) {
        return object.dynamicFieldValues().containsKey(name);
    }

    @Override
    public void write(String name, Object value) {
        object.dynamicFieldValues().put(name, value);
    }

    @Override
    public List<FieldRef> fields() {
        if (schema != null) {
            // Authoritative + complete: typed even for a null/absent or single value.
            return schema.fields();
        }

        List<FieldRef> out = new ArrayList<>();

        for (Map.Entry<String, Object> e
                : object.dynamicFieldValues().entrySet()) {

            out.add(fieldRef(e.getKey(), e.getValue()));
        }

        return out;
    }

    private static FieldRef fieldRef(String name, Object value) {
        boolean collection =
                value instanceof Collection<?>
                        || (value != null
                        && value.getClass().isArray());

        boolean reference =
                value instanceof Viewable
                        || (value instanceof Collection<?> c
                        && anyViewable(c));

        String typeLabel =
                value == null
                        ? null
                        : value.getClass().getSimpleName();

        return FieldRef.of(
                name,
                FieldKind.ofValue(value),
                typeLabel,
                reference,
                collection,
                false);
    }

    private static boolean anyViewable(Collection<?> c) {
        for (Object o : c) {
            if (o instanceof Viewable) {
                return true;
            }
        }

        return false;
    }
}