package objectview.field;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.annotations.Link;
import objectview.annotations.DisplayField;
import objectview.viewconfig.ConfigFieldRowSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A {@link FieldSet} over a hand-written {@link Viewable}'s declared Java fields
 * (the "old" representation) — types come straight from the field's Java type.
 */
public final class ReflectionFieldSet implements FieldSet {

    private final Viewable object;

    public ReflectionFieldSet(Viewable object) {
        this.object = object;
    }

    @Override
    public Object read(String name) {
        Field f = ViewableAdapter.getField(object.getClass(), name);
        if (f == null) {
            return null;
        }

        try {
            f.setAccessible(true);
            return f.get(object);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @Override
    public boolean has(String name) {
        return ViewableAdapter.getField(object.getClass(), name) != null;
    }

    @Override
    public void write(String name, Object value) {
        Field f = ViewableAdapter.getField(object.getClass(), name);
        if (f == null) {
            throw new IllegalArgumentException(
                    "No field "
                            + object.getClass().getName()
                            + "."
                            + name);
        }

        try {
            f.setAccessible(true);
            f.set(object, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "Cannot set " + name + " on " + object,
                    e);
        }
    }

    @Override
    public List<FieldRef> fields() {
        List<FieldRef> out = new ArrayList<>();

        for (Field f : ViewableAdapter.getAllFields(object.getClass())) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }

            out.add(describe(f, object.getClass()));
        }

        return out;
    }

    /**
     * Canonical description of a declared Java field. Reflection-backed domains
     * use this exact method to build their immutable schema, so rendering,
     * persistence and transform pickers cannot disagree about generic element
     * types or cardinality.
     */
    public static FieldRef describe(Field field, Class<?> owner) {
        Class<?> type = field.getType();
        boolean collection = Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type) || type.isArray();
        Class<? extends Viewable> nested =
                ViewableFieldPaths.nestedViewableClass(field);
        boolean reference = nested != null || ViewableAdapter.isReference(field);
        String targetType = nested == null ? null : nested.getSimpleName();

        FieldKind valueKind;
        if (reference) {
            valueKind = FieldKind.REFERENCE;
        } else if (type.isArray()) {
            valueKind = FieldKind.ofClass(type.getComponentType());
        } else if (collection) {
            valueKind = FieldKind.ofTypeLabel(
                    elementTypeLabel(ConfigFieldRowSource.describeFieldType(
                            field, owner)));
        } else {
            valueKind = FieldKind.ofClass(type);
        }
        FieldKind kind = collection ? FieldKind.COLLECTION : valueKind;

        boolean link = ViewableAdapter.isLinkField(field);
        Link linkAnn = link ? field.getAnnotation(Link.class) : null;
        FieldRole role = field.isAnnotationPresent(DisplayField.class)
                ? FieldRole.DISPLAY : FieldRole.NONE;
        return FieldRef.described(
                field.getName(), field.getName(), role, kind, valueKind,
                ConfigFieldRowSource.describeFieldType(field, owner),
                reference, collection, targetType, false,
                ViewableAdapter.isMinorField(field),
                ViewableAdapter.isInline(field), link,
                linkAnn == null ? "" : linkAnn.text(),
                ViewableAdapter.isReference(field));
    }

    private static String elementTypeLabel(String label) {
        if (label == null) {
            return "";
        }
        int open = label.indexOf('<');
        int close = label.lastIndexOf('>');
        if (open >= 0 && close > open) {
            String element = label.substring(open + 1, close).trim();
            int comma = element.lastIndexOf(',');
            return comma < 0 ? element
                    : element.substring(comma + 1).trim();
        }
        return label.endsWith("[]")
                ? label.substring(0, label.length() - 2) : label;
    }
}
