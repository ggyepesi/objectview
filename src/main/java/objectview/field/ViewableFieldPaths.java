package objectview.field;

import objectview.Viewable;
import objectview.ViewableAdapter;

import objectview.media.ImagePane;
import objectview.viewconfig.ViewConfig;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ViewableFieldPaths {
    private ViewableFieldPaths() {}

    public record FieldPath(String title, List<String> path, Field leafField) {

        /** The dotted access path, e.g. {@code nominee.name}. */
        public String dotted() {
            return String.join(".", path);
        }

        /** The last segment (the leaf field name). */
        public String leaf() {
            return path.isEmpty() ? "" : path.get(path.size() - 1);
        }

        /** A FieldPath from a dotted string (no reflection {@link Field}). */
        public static FieldPath of(String dotted) {
            List<String> segments = dotted == null || dotted.isBlank()
                    ? List.of()
                    : List.of(dotted.split("\\."));
            return new FieldPath(dotted == null ? "" : dotted, segments, null);
        }
    }

    public interface FieldFilter {
        boolean accept(Field field);
    }

    public static final FieldFilter ALL_FIELDS = field -> true;

    public static final FieldFilter NOT_IMAGE_PANE_FIELDS =
            field -> field != null && !ImagePane.class.isAssignableFrom(field.getType());

    public static List<FieldPath> collect(ViewConfig config) {
        return collect(config, NOT_IMAGE_PANE_FIELDS);
    }

    public static List<FieldPath> collect(ViewConfig config,
                                          FieldFilter filter) {
        List<FieldPath> out = new ArrayList<>();

        if (config == null || config.getCls() == null) {
            return out;
        }

        if (!Viewable.class.isAssignableFrom(config.getCls())) {
            return out;
        }

        collect(
                config,
                config.getCls(),
                new ArrayList<>(),
                "",
                filter == null ? ALL_FIELDS : filter,
                out);

        // Identity (name/qid) is implied by "all fields" only. An EXPLICIT config
        // means exactly what it names — forcing name in regardless made search
        // hit on name even when the user unchecked it.
        if (config.isAllFields()) {
            ensureIdentityFields(config.getCls(), out);
        }

        return dedupByPath(out);
    }

    /**
     * Keeps the first {@link FieldPath} for each distinct access path, dropping
     * later duplicates. Two entries with the same path address the same value, so
     * surfacing both only lets a stray/duplicated field (classically a second
     * {@code name}, before canonicalization) double the identity in sort/search/
     * config — with an inconsistent composite sort key as the symptom. The first
     * occurrence wins, so the canonical/identity entry (emitted first) is the one
     * kept. See docs/canonicalization-model.md.
     */
    static List<FieldPath> dedupByPath(List<FieldPath> paths) {
        List<FieldPath> out = new ArrayList<>();
        Set<List<String>> seen = new LinkedHashSet<>();
        for (FieldPath p : paths) {
            if (p != null && seen.add(p.path())) {
                out.add(p);
            }
        }
        return out;
    }

    /** How deep to follow references when enumerating from a sample instance. */
    private static final int SAMPLE_MAX_DEPTH = 2;

    /**
     * Field paths enumerated from a SAMPLE INSTANCE — so a {@link DynamicFields}
     * object (a map-backed Viewable) whose fields live in a property map
     * (not declared Java fields) still yields its fields, with nested paths followed
     * through reference values up to {@link #SAMPLE_MAX_DEPTH}. A reflection Viewable
     * falls back to its declared fields. Branch-cycle-safe. This makes the nested,
     * typed field model work over dynamic domains, not just reflected ones.
     */
    public static List<FieldPath> collectFromSample(Viewable sample, FieldFilter filter) {
        List<FieldPath> out = new ArrayList<>();
        if (sample == null) {
            return out;
        }
        out.add(new FieldPath("name", List.of("name"), null));   // identity/display
        collectSample(sample, new ArrayList<>(), "",
                filter == null ? ALL_FIELDS : filter,
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()),
                out);
        return dedupByPath(out);
    }

    private static void collectSample(Viewable obj, List<String> prefix, String titlePrefix,
                                      FieldFilter filter, Set<Object> branch, List<FieldPath> out) {
        if (obj == null || !branch.add(obj)) {
            return;
        }
        try {
            // ONE field model over both representations — declared Java fields OR the
            // dynamic property map (objectview.field.FieldSet), no instanceof branch. A
            // reflected field still resolves its java.lang.reflect.Field, to honour
            // the field filter / provenance skip and carry annotations (@Numeric)
            // downstream; a map-held (dynamic) field has none, so getField is null.
            FieldSet set = FieldSet.of(obj);
            for (FieldRef ref : set.fields()) {
                Field leaf = ViewableAdapter.getField(obj.getClass(), ref.name());
                if (leaf != null
                        && (!filter.accept(leaf) || ViewableAdapter.isProvenanceField(leaf))) {
                    continue;
                }
                addSampleField(ref.name(), set.read(ref.name()), leaf,
                        prefix, titlePrefix, filter, branch, out);
            }
        } finally {
            branch.remove(obj);
        }
    }

    private static void addSampleField(String name, Object value, Field leaf,
                                       List<String> prefix, String titlePrefix,
                                       FieldFilter filter, Set<Object> branch, List<FieldPath> out) {
        List<String> path = new ArrayList<>(prefix);
        path.add(name);
        String title = titlePrefix.isEmpty() ? name : titlePrefix + "." + name;

        Viewable child = firstViewable(value);
        if (child != null) {
            // A reference: offer the reference ITSELF (for invert / group-by-
            // reference), its display name, and (bounded) its nested fields.
            out.add(new FieldPath(title, path, leaf));
            List<String> namePath = new ArrayList<>(path);
            namePath.add("name");
            out.add(new FieldPath(title + ".name", namePath, leaf));
            if (prefix.size() < SAMPLE_MAX_DEPTH) {
                collectSample(child, path, title, filter, branch, out);
            }
        } else {
            out.add(new FieldPath(title, path, leaf));
        }
    }

    private static Viewable firstViewable(Object v) {
        if (v instanceof Viewable q) {
            return q;
        }
        if (v instanceof Collection<?> c) {
            for (Object i : c) {
                if (i instanceof Viewable q) return q;
            }
        }
        if (v instanceof Map<?, ?> m) {
            for (Object i : m.values()) {
                if (i instanceof Viewable q) return q;
            }
        }
        return null;
    }

    private static void collect(ViewConfig config,
                                Class<?> cls,
                                List<String> prefix,
                                String titlePrefix,
                                FieldFilter filter,
                                List<FieldPath> out) {
        if (config == null || cls == null || !Viewable.class.isAssignableFrom(cls)) {
            return;
        }

        Set<String> alreadyAdded = new LinkedHashSet<>();

        // 1. Explicit fields first, in config/editor order.
        for (Map.Entry<String, ViewConfig> e : config.getFields().entrySet()) {
            String fieldName = e.getKey();

            if ("name".equals(fieldName)) {
                addNamePath(prefix, titlePrefix, out);
                alreadyAdded.add("name");
                continue;
            }

            Field field = ViewableAdapter.getField(cls, fieldName);

            if (field != null && filter.accept(field)) {
                collectField(
                        field,
                        e.getValue(),
                        prefix,
                        titlePrefix,
                        filter,
                        out);

                alreadyAdded.add(fieldName);
            } else if (field == null) {
                // A DYNAMIC (map-held) field: the config names it but there is no
                // declared Java field to reflect on (e.g. a snapshot WDO's `won`).
                // Emit the path anyway — extraction reads the property map.
                addDynamicPath(fieldName, e.getValue(), prefix, titlePrefix, out);
                alreadyAdded.add(fieldName);
            }
        }

        // 2. Add implicit allFields/allMinorFields only after explicit fields.
        for (Field field : ViewableAdapter.getAllFields(cls)) {
            String fieldName = field.getName();

            if (alreadyAdded.contains(fieldName)) {
                continue;
            }

            if ("name".equals(fieldName)) {
                if ((config.isAllFields() || config.getFields().containsKey("name"))
                        && !alreadyAdded.contains("name")) {
                    addNamePath(prefix, titlePrefix, out);
                    alreadyAdded.add("name");
                }
                continue;
            }

            if (!config.showsField(field)) {
                continue;
            }

            collectField(
                    field,
                    config.getFieldConfig(fieldName),
                    prefix,
                    titlePrefix,
                    filter,
                    out);
        }
    }

    // A config-named field with no declared Java Field (dynamic/map-held): the
    // leaf is null, the path still resolves via the property map. A child config
    // recurses the same way (the nested class is unknown at collect time).
    private static void addDynamicPath(String fieldName,
                                       ViewConfig childConfig,
                                       List<String> prefix,
                                       String titlePrefix,
                                       List<FieldPath> out) {
        List<String> path = new ArrayList<>(prefix);
        path.add(fieldName);
        String title = titlePrefix.isEmpty()
                ? fieldName
                : titlePrefix + "." + fieldName;

        if (childConfig == null || childConfig.getFields().isEmpty()) {
            out.add(new FieldPath(title, path, null));
            return;
        }
        for (Map.Entry<String, ViewConfig> e
                : childConfig.getFields().entrySet()) {
            addDynamicPath(e.getKey(), e.getValue(), path, title, out);
        }
    }

    private static void addNamePath(List<String> prefix,
                                    String titlePrefix,
                                    List<FieldPath> out) {
        List<String> namePath = new ArrayList<>(prefix);
        namePath.add("name");

        String title = titlePrefix.isEmpty()
                ? "name"
                : titlePrefix + ".name";

        out.add(new FieldPath(title, namePath, null));
    }

    // Identity fields (name + qid) are @Hidden — hidden from the CARD
    // (they're the title/identity) but still meaningful to search/sort/configure
    // by. Without this a bare reference object (a WikidataDynamicObject with no
    // dynamic fields) offers nothing to configure. Scoped to entity objects (those
    // that declare a `qid` field) so non-Wikidata Viewables are untouched.
    private static void ensureIdentityFields(Class<?> cls, List<FieldPath> out) {
        Field qid = rawDeclaredField(cls, "qid");
        if (qid == null) {
            return;
        }
        if (!hasRootPath(out, "name")) {
            out.add(new FieldPath("name", List.of("name"), null));
        }
        if (!hasRootPath(out, "qid")) {
            qid.setAccessible(true);
            out.add(new FieldPath("qid", List.of("qid"), qid));
        }
    }

    private static boolean hasRootPath(List<FieldPath> out, String name) {
        for (FieldPath p : out) {
            if (p.path().size() == 1 && name.equals(p.path().get(0))) {
                return true;
            }
        }
        return false;
    }

    // Finds a declared field by name up the hierarchy, INCLUDING @Hidden
    // ones (which ViewableAdapter.getField deliberately omits).
    private static Field rawDeclaredField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        return null;
    }

    private static void collectField(Field field,
                                     ViewConfig childConfig,
                                     List<String> prefix,
                                     String titlePrefix,
                                     FieldFilter filter,
                                     List<FieldPath> out) {
        if (field == null || !filter.accept(field)) {
            return;
        }
        // Provenance (the Source chip) is metadata, not a searchable/sortable
        // domain field — keep it out of the field paths.
        if (ViewableAdapter.isProvenanceField(field)) {
            return;
        }

        String fieldName = field.getName();

        List<String> path = new ArrayList<>(prefix);
        path.add(fieldName);

        String title = titlePrefix.isEmpty()
                ? fieldName
                : titlePrefix + "." + fieldName;

        Class<?> nested = nestedViewableClass(field);

        if (nested != null) {
            if (childConfig != null
                    && (childConfig.isAllFields()
                    || childConfig.isAllMinorFields()
                    || !childConfig.getFields().isEmpty())) {

                ViewConfig child = childConfig.copy();
                child.setCls(asViewableClass(nested));

                collect(
                        child,
                        nested,
                        path,
                        title,
                        filter,
                        out);
            } else {
                List<String> namePath = new ArrayList<>(path);
                namePath.add("name");

                out.add(new FieldPath(
                        title + ".name",
                        namePath,
                        field));
            }

            return;
        }

        out.add(new FieldPath(title, path, field));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Viewable> asViewableClass(Class<?> cls) {
        return (Class<? extends Viewable>) cls;
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Viewable> nestedViewableClass(Field field) {
        if (field == null) {
            return null;
        }

        Class<?> type = field.getType();

        if (ImagePane.class.isAssignableFrom(type)) {
            return null;
        }

        if (Viewable.class.isAssignableFrom(type)) {
            return (Class<? extends Viewable>) type;
        }

        if (Collection.class.isAssignableFrom(type)) {
            Type g = field.getGenericType();

            if (g instanceof ParameterizedType pt) {
                Type arg = pt.getActualTypeArguments()[0];

                if (arg instanceof Class<?> c && Viewable.class.isAssignableFrom(c)) {
                    return (Class<? extends Viewable>) c;
                }
            }

            return null;
        }

        if (Map.class.isAssignableFrom(type)) {
            Type g = field.getGenericType();

            if (g instanceof ParameterizedType pt) {
                Type value = pt.getActualTypeArguments()[1];

                if (value instanceof Class<?> c && Viewable.class.isAssignableFrom(c)) {
                    return (Class<? extends Viewable>) c;
                }
            }

            return null;
        }

        return null;
    }
}