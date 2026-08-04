package objectview.field;

import objectview.Viewable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A {@link FieldSet} over a {@link DynamicFields} object's property map (the "new"
 * representation — e.g. {@code WikidataDynamicObject}). This backing describes
 * observed values; {@link SchemaFieldSet} overlays authoritative metadata when
 * a {@link FieldSchema} is available.
 */
public final class DynamicFieldSet implements FieldSet {

    private final DynamicFields object;

    public DynamicFieldSet(DynamicFields object) {
        this.object = object;
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
                        || value instanceof Map<?, ?>
                        || (value != null
                        && value.getClass().isArray());

        Object element = collection ? firstElement(value) : value;
        boolean reference = element instanceof Viewable;
        FieldKind valueKind = FieldKind.ofValue(element);
        FieldKind kind = collection ? FieldKind.COLLECTION : valueKind;
        String targetType = reference
                ? ((Viewable) element).typeName() : null;

        String elementLabel = element == null
                ? "Object" : reference
                ? targetType : element.getClass().getSimpleName();
        String typeLabel = value instanceof Map<?, ?> map
                ? "Map<" + keyTypeLabel(map) + ", " + elementLabel + ">"
                : collection
                ? "Collection<" + elementLabel + ">"
                : value == null ? null : elementLabel;

        return FieldRef.described(name, kind, valueKind, typeLabel,
                reference, collection, targetType, false, false,
                false, false, "", false);
    }

    private static Object firstElement(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(value, i);
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
        return value;
    }

    private static String keyTypeLabel(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            if (key != null) {
                return key.getClass().getSimpleName();
            }
        }
        return "Object";
    }
}
