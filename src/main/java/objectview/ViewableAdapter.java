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

    public static boolean isValidQuizValue(Object value) {
        return switch (value) {
            case null -> false;
            case String s -> !s.isBlank();
            case Collection<?> c -> !c.isEmpty();
            case Map<?, ?> m -> !m.isEmpty();
            default -> true;
        };
    }

    public static List<Field> getAllFields(Class<?> cls) {
        if (cls == null) {
            return Collections.emptyList();
        }

        return ALL_FIELDS_CACHE.computeIfAbsent(
                cls,
                ViewableAdapter::collectAllFields);
    }

    /**
     * Fields offered in CONFIG pickers (viewconfig / search / sort):
     * {@link #getAllFields} PLUS the identity fields (name, qid) for entity
     * objects.
     *
     * <p>Those are {@code @Hidden} — hidden from the card — but are
     * legitimately searchable / sortable / configurable, and a bare reference
     * object (a WikidataDynamicObject with no dynamic fields) has nothing else
     * to offer. Non-entity Viewables (no {@code qid} field) are unchanged.</p>
     */
    public static List<Field> getConfigurableFields(Class<?> cls) {
        List<Field> all = getAllFields(cls);

        Field qid = rawDeclaredField(cls, "qid");
        if (qid == null) {
            return all; // not an entity object — leave as-is
        }

        List<Field> out = new ArrayList<>();

        Field name = rawDeclaredField(cls, "name");
        if (name != null) {
            name.setAccessible(true);
            out.add(name);
        }

        qid.setAccessible(true);
        out.add(qid);
        out.addAll(all);

        // (diagnostic) a field that duplicates the identity name/qid — e.g.
        // an un-annotated `name` leaking into getAllFields — shows twice in config.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Field f : out) {
            if (!seen.add(f.getName())) {
                log.debug("configurable fields: duplicate '{}' in {}",
                        f.getName(), cls.getName());
            }
        }

        return out;
    }

    // Declared field by name up the hierarchy, INCLUDING @Hidden ones
    // (which getAllFields deliberately drops).
    private static Field rawDeclaredField(Class<?> cls, String name) {
        for (Class<?> c = cls;
             c != null;
             c = c.getSuperclass()) {

            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }

        return null;
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
