package objectview;

import objectview.annotations.*;
import objectview.field.FieldSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-backed base implementation shared by Viewable objects.
 *
 * <p>Holds the reflection and field-presence machinery; a host's own adapter
 * (which adds projection and construction) extends this.</p>
 */
public abstract class ViewableAdapter implements Viewable {

    @Hidden
    private static final Logger log = LoggerFactory.getLogger(ViewableAdapter.class);

    @Hidden
    private static final Map<Class<?>, List<Field>> ALL_FIELDS_CACHE =
            new ConcurrentHashMap<>();

    @Hidden
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE =
            new ConcurrentHashMap<>();

    @Hidden
    private transient FieldSet fieldSet;

    /** Ordinary instance provenance. The annotation changes presentation/traversal,
     * not field visibility: source and its nested fields remain configurable and
     * validatable. */
    @Provenance
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient objectview.provenance.Source source;

    @Override public objectview.provenance.Source source() { return source; }

    @Override public void source(objectview.provenance.Source source) {
        this.source = source;
    }

    public static boolean isMinorField(Field field) {
        return field != null && field.isAnnotationPresent(Minor.class);
    }

    public static boolean isReference(Field field) {
        return field != null
                && field.isAnnotationPresent(Reference.class);
    }

    public static boolean isInline(Field field) {
        return field != null
                && field.isAnnotationPresent(Inline.class);
    }

    public static boolean isLinkField(Field field) {
        return field != null && field.isAnnotationPresent(Link.class);
    }

    public static boolean isProvenanceField(Field field) {
        return field != null && field.isAnnotationPresent(Provenance.class);
    }

    public static boolean isHidden(Field field) {
        return field != null
                && field.isAnnotationPresent(Hidden.class);
    }

    /** Whether {@code value} has something worth rendering. A blank string is absent; a
     *  collection/map counts as present only if SOME element is itself a valid (non-blank)
     *  value — so an all-blank collection (e.g. {@code [""]}) renders as nothing rather
     *  than a phantom one-item field. */
    public static boolean isValidQuizValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        if (value instanceof Collection<?> c) {
            return c.stream().anyMatch(ViewableAdapter::isValidQuizValue);
        }
        if (value instanceof Map<?, ?> m) {
            return m.values().stream().anyMatch(ViewableAdapter::isValidQuizValue);
        }
        return true;
    }

    public static List<Field> getAllFields(Class<?> cls) {
        if (cls == null) {
            return Collections.emptyList();
        }

        return ALL_FIELDS_CACHE.computeIfAbsent(
                cls,
                ViewableAdapter::collectAllFields);
    }

    private static List<Field> collectAllFields(Class<?> cls) {
        Class<?> current = cls;
        List<Field> fields = new ArrayList<>();

        while (current != null && current.getSuperclass() != null) {
            // Don't reflect into JDK classes (e.g. a Viewable that extends
            // ArrayList): their fields aren't view data, and setAccessible on
            // them throws InaccessibleObjectException under JPMS
            // (java.base doesn't open java.util). Stop the walk here — the
            // remaining superclasses are JDK too.
            if (isSystemClass(current)) {
                break;
            }

            for (Field field : current.getDeclaredFields()) {
                // static fields are class-level state (e.g. a cache), never
                // per-instance view data — skip them.
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                if (isHidden(field)) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                } catch (RuntimeException inaccessible) {
                    continue; // e.g. a JDK/module-closed field — skip it
                }

                fields.add(field);
            }

            current = current.getSuperclass();
        }

        return Collections.unmodifiableList(fields);
    }

    private static boolean isSystemClass(Class<?> cls) {
        String pkg = cls.getPackageName();

        return pkg.startsWith("java.")
                || pkg.startsWith("javax.")
                || pkg.startsWith("jdk.")
                || pkg.startsWith("sun.");
    }

    public static Field getField(Class<?> cls, String name) {
        if (cls == null || name == null) {
            return null;
        }

        return FIELD_CACHE
                .computeIfAbsent(cls, ViewableAdapter::buildFieldMap)
                .get(name);
    }

    private static Map<String, Field> buildFieldMap(Class<?> cls) {
        Map<String, Field> map = new ConcurrentHashMap<>();

        for (Field field : getAllFields(cls)) {
            map.putIfAbsent(field.getName(), field);
        }

        return map;
    }

    public static void clearReflectionCaches() {
        ALL_FIELDS_CACHE.clear();
        FIELD_CACHE.clear();
    }

    @Override
    public FieldSet fields() {
        if (fieldSet == null) {
            fieldSet = FieldSet.of(this);
        }
        return fieldSet;
    }

    public boolean hasField(String fieldName) {
        try {
            return hasField(getField(getClass(), fieldName));
        } catch (Exception e) {
            log.warn("hasField('{}') on {} failed", fieldName, getClass().getName(), e);
            return false;
        }
    }

    public boolean hasFields(Collection<String> fieldNames) {
        for (String fieldName : fieldNames) {
            if (!hasField(fieldName)) {
                return false;
            }
        }

        return true;
    }

    public boolean hasAnyField() {
        for (Field field : getAllFields(getClass())) {
            if (hasField(field)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasField(Field field) {
        if (field == null) {
            return false;
        }

        try {
            Object fieldValue = field.get(this);
            if (fieldValue == null) {
                return false;
            }

            Class<?> fieldType = field.getType();

            if (Collection.class.isAssignableFrom(fieldType)) {
                return !((Collection<?>) fieldValue).isEmpty();
            } else if (Map.class.isAssignableFrom(fieldType)) {
                return !((Map<?, ?>) fieldValue).isEmpty();
            } else if (Viewable.class.isAssignableFrom(fieldType)) {
                Viewable viewable = (Viewable) fieldValue;
                return !(viewable instanceof ViewableAdapter adapter)
                        || adapter.hasAnyField();
            }
        } catch (Exception e) {
            log.warn("hasField reflection on {} failed", getClass().getName(), e);
            return false;
        }

        return true;
    }
}
