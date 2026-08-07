package objectview.field;

import objectview.Viewable;
import objectview.ViewableAdapter;

import objectview.media.ImagePane;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.FieldTypeSource;

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

    /** Presentation/metadata attached to one canonical access path. {@code valueKind} is
     *  the leaf's value kind (ORDERED / TEXT / …) — carried so consumers like sort know a
     *  field is numeric from the schema (a persisted {@code @Numeric}), not only from a
     *  reflection {@link Field} that a dynamic/snapshot path lacks. */
    public record PathInfo(String title, FieldPath path, Field leafField, FieldKind valueKind) {
        /** Derives {@code valueKind} from the leaf reflection field (UNKNOWN when none). */
        public PathInfo(String title, FieldPath path, Field leafField) {
            this(title, path, leafField, leafField == null
                    ? FieldKind.UNKNOWN : FieldKind.ofClass(leafField.getType()));
        }
        public String dotted() { return path.dotted(); }
        public String leaf() { return path.leaf(); }
    }

    public interface FieldFilter {
        boolean accept(Field field);
    }

    public static final FieldFilter ALL_FIELDS = field -> true;

    /** Excludes MEDIA-kind fields (a renderable image — {@code ImagePane} or any
     *  {@code MediaValue}). Media can't be text-searched or ordered, so search / sort /
     *  filter enumeration drops it by KIND (not by a single concrete class). Contexts
     *  that DO want media — the view config, coverage/validation — pass {@link #ALL_FIELDS}. */
    public static final FieldFilter NOT_MEDIA_FIELDS =
            field -> field != null
                    && objectview.field.FieldKind.ofClass(field.getType()) != FieldKind.MEDIA;

    public static List<PathInfo> collect(ViewConfig config) {
        return collect(config, NOT_MEDIA_FIELDS);
    }

    public static List<PathInfo> collect(ViewConfig config,
                                          FieldFilter filter) {
        List<PathInfo> out = new ArrayList<>();

        if (config == null || config.getCls() == null) {
            return out;
        }

        if (!Viewable.class.isAssignableFrom(config.getCls())) {
            return out;
        }

        collect(
                config,
                config.getCls(),
                FieldPath.ROOT,
                "",
                filter == null ? ALL_FIELDS : filter,
                out);

        // Contract views are implied by "all fields" only. An explicit config means
        // exactly what it names.
        if (config.isAllFields()) {
            ensureContractFields(out, config.getCls());
        }

        return dedupByPath(out);
    }

    /**
     * Enumerates configured paths exclusively from an authoritative schema
     * projection. No instance value or reflected field participates in discovery,
     * so null/empty fields and dynamic/reflected representations yield the same
     * stable paths.
     */
    public static List<PathInfo> collectFromSchema(
            ViewConfig config, FieldTypeSource schema, boolean excludeMedia) {
        List<PathInfo> out = new ArrayList<>();
        if (config == null || schema == null) return out;
        collectSchema(config, schema, FieldPath.ROOT, "", excludeMedia,
                new LinkedHashSet<>(), out);
        return dedupByPath(out);
    }

    private static void collectSchema(
            ViewConfig config, FieldTypeSource schema, FieldPath prefix,
            String titlePrefix, boolean excludeMedia,
            Set<FieldTypeSource> branch, List<PathInfo> out) {
        if (config == null || schema == null || !branch.add(schema)) return;
        try {
            LinkedHashSet<String> names = new LinkedHashSet<>(config.getFields().keySet());
            names.addAll(schema.fieldNames());
            for (String name : names) {
                FieldTypeSource.FieldTypeInfo info = schema.field(name);
                if (info == null || info.structural()) continue;
                boolean explicit = config.getFields().containsKey(name);
                boolean selected = explicit || (info.minor()
                        ? config.isAllMinorFields() : config.isAllFields());
                if (!selected || excludeMedia
                        && (info.kind() == FieldKind.MEDIA
                        || info.valueKind() == FieldKind.MEDIA)) continue;

                FieldPath path = prefix.append(name);
                String label = info.label() == null || info.label().isBlank()
                        ? name : info.label();
                String title = titlePrefix.isEmpty() ? label : titlePrefix + "." + label;
                ViewConfig child = config.getFieldConfig(name);
                boolean childSelected = child != null && (child.isAllFields()
                        || child.isAllMinorFields() || !child.getFields().isEmpty());
                if (info.nested() != null && childSelected) {
                    collectSchema(child, info.nested(), path, title, excludeMedia,
                            branch, out);
                } else if (info.nested() != null) {
                    // The configured reference is this path. Its display label is
                    // the searchable value of the reference, not an invented child.
                    out.add(new PathInfo(title, path, null, info.valueKind()));
                } else {
                    // Carry the schema's value kind (e.g. a persisted @Numeric -> ORDERED)
                    // so sort reads this dynamic leaf as a number without a reflection field.
                    out.add(new PathInfo(title, path, null, info.valueKind()));
                }
            }
        } finally {
            branch.remove(schema);
        }
    }

    /**
     * Field paths enumerated from a sample instance, restricted recursively by
     * {@code config}. Unlike filtering only the first path segment, this method
     * applies each child {@link ViewConfig} at the level it configures.
     * <p>
     * The sample is still used to discover map-backed dynamic fields. Declared
     * fields retain their reflection {@link Field}, annotations and field filter.
     */
    public static List<PathInfo> collectFromSample(Viewable sample,
                                                    ViewConfig config,
                                                    FieldFilter filter) {
        List<PathInfo> out = new ArrayList<>();

        if (sample == null || config == null) {
            return out;
        }

        collectConfiguredSample(
                sample,
                config,
                FieldPath.ROOT,
                "",
                filter == null ? ALL_FIELDS : filter,
                java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<>()),
                out);

        return dedupByPath(out);
    }

    private static void collectConfiguredSample(Viewable obj,
                                                ViewConfig config,
                                                FieldPath prefix,
                                                String titlePrefix,
                                                FieldFilter filter,
                                                Set<Object> branch,
                                                List<PathInfo> out) {
        if (obj == null || config == null || !branch.add(obj)) {
            return;
        }

        try {
            FieldSet set = FieldSet.of(obj);
            Set<String> handled = new LinkedHashSet<>();

            // Explicit fields first, preserving configuration order.
            for (Map.Entry<String, ViewConfig> entry
                    : config.getFields().entrySet()) {

                String name = entry.getKey();

                addConfiguredSampleField(
                        obj,
                        set,
                        name,
                        entry.getValue(),
                        prefix,
                        titlePrefix,
                        filter,
                        branch,
                        out);

                handled.add(name);
            }

            // Then fields implied by allFields/allMinorFields. These are read from
            // the actual sample so map-backed dynamic fields are included too.
            for (FieldRef ref : set.fields()) {
                String name = ref.name();

                if (handled.contains(name)) {
                    continue;
                }

                Field field =
                        ViewableAdapter.getField(obj.getClass(), name);

                boolean shown = field != null
                        ? config.showsField(field)
                        : config.showsFieldByName(name);

                if (!shown) {
                    continue;
                }

                addConfiguredSampleField(
                        obj,
                        set,
                        name,
                        config.getFieldConfig(name),
                        prefix,
                        titlePrefix,
                        filter,
                        branch,
                        out);
            }
        } finally {
            branch.remove(obj);
        }
    }

    private static void addConfiguredSampleField(Viewable owner,
                                                 FieldSet set,
                                                 String name,
                                                 ViewConfig childConfig,
                                                 FieldPath prefix,
                                                 String titlePrefix,
                                                 FieldFilter filter,
                                                 Set<Object> branch,
                                                 List<PathInfo> out) {
        Field leaf =
                ViewableAdapter.getField(owner.getClass(), name);

        FieldRef ref = set.field(name);
        String label = ref == null ? name : ref.label();

        if (leaf != null && !filter.accept(leaf)) {
            return;
        }

        Object value = set.has(name)
                ? set.read(name)
                : null;

        FieldPath path = prefix.append(name);

        String title = titlePrefix.isEmpty()
                ? label
                : titlePrefix + "." + label;

        Viewable child = firstViewable(value);

        if (child == null) {
            out.add(new PathInfo(title, path, leaf));
            return;
        }

        boolean childHasSelection = childConfig != null
                && (childConfig.isAllFields()
                || childConfig.isAllMinorFields()
                || !childConfig.getFields().isEmpty());

        if (childHasSelection && prefix.size() < SAMPLE_MAX_DEPTH) {
            collectConfiguredSample(
                    child,
                    childConfig,
                    path,
                    title,
                    filter,
                    branch,
                    out);
        } else {
            out.add(new PathInfo(title, path, leaf));
        }
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
    static List<PathInfo> dedupByPath(List<PathInfo> paths) {
        List<PathInfo> out = new ArrayList<>();
        Set<FieldPath> seen = new LinkedHashSet<>();
        for (PathInfo p : paths) {
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
    public static List<PathInfo> collectFromSample(Viewable sample, FieldFilter filter) {
        List<PathInfo> out = new ArrayList<>();
        if (sample == null) {
            return out;
        }
        collectSample(sample, FieldPath.ROOT, "",
                filter == null ? ALL_FIELDS : filter,
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()),
                out);
        return dedupByPath(out);
    }

    private static void collectSample(Viewable obj, FieldPath prefix, String titlePrefix,
                                      FieldFilter filter, Set<Object> branch, List<PathInfo> out) {
        if (obj == null || !branch.add(obj)) {
            return;
        }
        try {
            // ONE field model over both representations — declared Java fields OR the
            // dynamic property map (objectview.field.FieldSet), no instanceof branch. A
            // reflected field still resolves its java.lang.reflect.Field, to honour
            // the field filter and carry annotations (@Numeric)
            // downstream; a map-held (dynamic) field has none, so getField is null.
            FieldSet set = FieldSet.of(obj);
            for (FieldRef ref : set.fields()) {
                Field leaf = ViewableAdapter.getField(obj.getClass(), ref.name());
                if (leaf != null && !filter.accept(leaf)) {
                    continue;
                }
                addSampleField(ref.name(), ref.label(), set.read(ref.name()), leaf,
                        prefix, titlePrefix, filter, branch, out);
            }
        } finally {
            branch.remove(obj);
        }
    }

    private static void addSampleField(String name, String label, Object value, Field leaf,
                                       FieldPath prefix, String titlePrefix,
                                       FieldFilter filter, Set<Object> branch, List<PathInfo> out) {
        FieldPath path = prefix.append(name);
        String title = titlePrefix.isEmpty() ? label : titlePrefix + "." + label;

        Viewable child = firstViewable(value);
        if (child != null) {
            // A reference: offer the reference ITSELF (for invert / group-by-
            // reference), its display name, and (bounded) its nested fields.
            out.add(new PathInfo(title, path, leaf));
            String displayKey = ViewableContractFieldSet.displayKey(FieldSet.of(child));
            FieldPath namePath = path.append(displayKey);
            out.add(new PathInfo(
                    title + "." + ViewableContractFieldSet.label(
                            displayKey),
                    namePath, leaf));
            if (prefix.size() < SAMPLE_MAX_DEPTH) {
                collectSample(child, path, title, filter, branch, out);
            }
        } else {
            out.add(new PathInfo(title, path, leaf));
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
                                FieldPath prefix,
                                String titlePrefix,
                                FieldFilter filter,
                                List<PathInfo> out) {
        if (config == null || cls == null || !Viewable.class.isAssignableFrom(cls)) {
            return;
        }

        Set<String> alreadyAdded = new LinkedHashSet<>();

        // 1. Explicit fields first, in config/editor order.
        for (Map.Entry<String, ViewConfig> e : config.getFields().entrySet()) {
            String fieldName = e.getKey();

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
                                       FieldPath prefix,
                                       String titlePrefix,
                                       List<PathInfo> out) {
        FieldPath path = prefix.append(fieldName);
        String title = titlePrefix.isEmpty()
                ? ViewableContractFieldSet.label(fieldName)
                : titlePrefix + "." + ViewableContractFieldSet.label(fieldName);

        if (childConfig == null || childConfig.getFields().isEmpty()) {
            out.add(new PathInfo(title, path, null));
            return;
        }
        for (Map.Entry<String, ViewConfig> e
                : childConfig.getFields().entrySet()) {
            addDynamicPath(e.getKey(), e.getValue(), path, title, out);
        }
    }

    private static void ensureContractFields(
            List<PathInfo> out, Class<? extends Viewable> type) {
        if (!ViewableContractFieldSet.DISPLAY_KEY.equals(
                ViewableContractFieldSet.displayKey(type))) {
            return;
        }
        for (FieldRef field : ViewableContractFieldSet.fieldRefs()) {
            if (!hasRootPath(out, field.name())) {
                out.add(new PathInfo(
                        field.label(), FieldPath.of(field.name()), null));
            }
        }
    }

    private static boolean hasRootPath(List<PathInfo> out, String name) {
        for (PathInfo p : out) {
            if (p.path().size() == 1
                    && name.equals(p.path().segments().get(0))) {
                return true;
            }
        }
        return false;
    }

    private static void collectField(Field field,
                                     ViewConfig childConfig,
                                     FieldPath prefix,
                                     String titlePrefix,
                                     FieldFilter filter,
                                     List<PathInfo> out) {
        if (field == null || !filter.accept(field)) {
            return;
        }
        String fieldName = field.getName();

        FieldPath path = prefix.append(fieldName);

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
                String displayKey = ViewableContractFieldSet.displayKey(
                        asViewableClass(nested));
                FieldPath namePath = path.append(displayKey);

                out.add(new PathInfo(
                        title + "." + ViewableContractFieldSet.label(
                                displayKey),
                        namePath,
                        field));
            }

            return;
        }

        out.add(new PathInfo(title, path, field));
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
