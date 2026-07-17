package objectview.search;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.ViewableFieldPaths;

import objectview.annotations.Numeric;
import objectview.render.Card;
import objectview.viewconfig.ViewConfig;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-UI helper for SearchPanel.
 *
 * It owns:
 * - cached field extraction
 * - search index
 * - token matching
 * - decorated/precomputed sort keys
 *
 * It deliberately does NOT own:
 * - Swing highlighting
 * - dialogs
 * - navigation result rows
 */
public class SearchAndSort {

    private final Map<Class<?>, Map<String, Field>> fieldCache =
            new ConcurrentHashMap<>();

    private final List<SearchEntry> searchIndex =
            new ArrayList<>();

    public void rebuildSearchIndex(
            JComponent targetPanel,
            ViewConfig searchConfig) {

        searchIndex.clear();

        if (targetPanel == null || searchConfig == null) {
            return;
        }

        List<ViewableFieldPaths.FieldPath> paths =
                ViewableFieldPaths.collect(
                        searchConfig,
                        ViewableFieldPaths.NOT_IMAGE_PANE_FIELDS);

        for (Component c : targetPanel.getComponents()) {
            if (!(c instanceof Card qp)) {
                continue;
            }

            Map<String, String> fieldTextByTitle =
                    new LinkedHashMap<>();

            for (ViewableFieldPaths.FieldPath fp : paths) {
                Object value =
                        extractValue(qp.getViewable(), fp.path());

                fieldTextByTitle.put(
                        fp.title(),
                        normalize(flattenForSearch(value)));
            }

            searchIndex.add(new SearchEntry(qp, fieldTextByTitle));
        }
    }

    public Map<String, List<Card>> search(
            List<String> queryTokens) {

        Map<String, List<Card>> out =
                new LinkedHashMap<>();

        if (queryTokens == null || queryTokens.isEmpty()) {
            return out;
        }

        for (SearchEntry entry : searchIndex) {
            for (Map.Entry<String, String> field
                    : entry.fieldTextByTitle.entrySet()) {

                if (!containsAllTokens(field.getValue(), queryTokens)) {
                    continue;
                }

                out.computeIfAbsent(
                        field.getKey(),
                        k -> new ArrayList<>())
                   .add(entry.panel);
            }
        }

        return out;
    }

    /** Data-centric search for the virtualized view: match the viewables themselves
     *  (not live components, of which only the visible ones exist) and return the
     *  matching viewables per field title, in field-then-data order. The caller
     *  navigates these hits one at a time, building each card on demand. */
    public Map<String, List<objectview.Viewable>> searchViewables(
            List<objectview.Viewable> viewables,
            List<String> queryTokens,
            ViewConfig searchConfig) {

        Map<String, List<objectview.Viewable>> out = new LinkedHashMap<>();

        if (viewables == null || viewables.isEmpty() || searchConfig == null
                || queryTokens == null || queryTokens.isEmpty()) {
            return out;
        }

        // Enumerate fields through the ONE unified bridge (FieldSet, via
        // collectFromSample) — it reads declared Java fields AND a WDO's map-held
        // fields with no instanceof branch, so a reference like forWork/category is
        // searchable regardless of representation. Filter to the search config's
        // selection (by the path's top-level field), so "search these fields" applies.
        List<ViewableFieldPaths.FieldPath> candidates = new ArrayList<>();
        for (ViewableFieldPaths.FieldPath fp
                : ViewableFieldPaths.collectFromSample(
                viewables.get(0), ViewableFieldPaths.NOT_IMAGE_PANE_FIELDS)) {
            String top = fp.path().isEmpty() ? "" : fp.path().get(0);
            if (searchConfig.showsFieldByName(top)) {
                candidates.add(fp);
            }
        }

        // A reference field is enumerated as BOTH the bare path (e.g. `hemisphere`,
        // which flattens to the referent's display name when searched) and an explicit
        // `hemisphere.name`. For SEARCH they match identical text, so keeping both
        // double-reports one hit under two titles. Drop the redundant `.name` child
        // when its bare reference path is also searched (the split still serves
        // invert / projection config, which is why it isn't removed at the source).
        Set<List<String>> present = new HashSet<>();
        for (ViewableFieldPaths.FieldPath fp : candidates) {
            present.add(fp.path());
        }
        List<ViewableFieldPaths.FieldPath> paths = new ArrayList<>();
        for (ViewableFieldPaths.FieldPath fp : candidates) {
            List<String> p = fp.path();
            if (p.size() >= 2
                    && "name".equals(p.get(p.size() - 1))
                    && present.contains(p.subList(0, p.size() - 1))) {
                continue;
            }
            paths.add(fp);
        }

        for (ViewableFieldPaths.FieldPath fp : paths) {
            List<objectview.Viewable> hits = null;

            for (objectview.Viewable q : viewables) {
                Object value = extractValue(q, fp.path());

                if (containsAllTokens(normalize(flattenForSearch(value)), queryTokens)) {
                    if (hits == null) {
                        hits = new ArrayList<>();
                    }
                    hits.add(q);
                }
            }

            if (hits != null) {
                out.put(fp.title(), hits);
            }
        }

        return out;
    }

    public List<Card> sortPanels(
            List<Card> panels,
            List<ViewableFieldPaths.FieldPath> sortPaths) {

        List<PanelSortKey> keyed =
                new ArrayList<>();

        for (Card panel : panels) {
            keyed.add(new PanelSortKey(
                    panel,
                    buildSortKey(panel, sortPaths)));
        }

        keyed.sort(Comparator.comparing(PanelSortKey::key));

        List<Card> out =
                new ArrayList<>(keyed.size());

        for (PanelSortKey key : keyed) {
            out.add(key.panel);
        }

        return out;
    }

    private String buildSortKey(
            Card panel,
            List<ViewableFieldPaths.FieldPath> paths) {

        StringBuilder sb =
                new StringBuilder();

        for (ViewableFieldPaths.FieldPath f : paths) {
            Object value =
                    extractValue(panel.getViewable(), f.path());

            // A @Numeric leaf field sorts by its leading number ("1538 K" ->
            // 1538), not lexically — driven by the annotation, not the value type.
            sb.append(sortKey(f.leafField(), value))
              .append('\u0000');
        }

        sb.append(sortableString(panel.getViewable()));

        return sb.toString();
    }

    /** Data-centric sort: order the viewables themselves by the sort paths — used
     *  by the virtualized view, which sorts data (not live components) then
     *  re-virtualizes. Reuses the same key logic, read straight from the viewable. */
    public List<objectview.Viewable> sortViewables(
            List<objectview.Viewable> viewables,
            List<ViewableFieldPaths.FieldPath> sortPaths) {

        List<objectview.Viewable> out = new ArrayList<>(viewables);
        out.sort(Comparator.comparing(q -> buildSortKeyQ(q, sortPaths)));
        return out;
    }

    private String buildSortKeyQ(
            objectview.Viewable viewable,
            List<ViewableFieldPaths.FieldPath> paths) {

        StringBuilder sb = new StringBuilder();
        for (ViewableFieldPaths.FieldPath f : paths) {
            Object value = extractValue(viewable, f.path());
            sb.append(sortKey(f.leafField(), value)).append((char) 0);
        }
        sb.append(sortableString(viewable));
        return sb.toString();
    }

    private Object extractValue(
            Object obj,
            List<String> path) {

        try {
            return extractRecursive(obj, path, 0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object extractRecursive(
            Object obj,
            List<String> path,
            int idx) throws Exception {

        if (obj == null) {
            return null;
        }

        if (idx >= path.size()) {
            return obj;
        }

        if (obj instanceof Collection<?> c) {
            List<Object> out =
                    new ArrayList<>();

            for (Object item : c) {
                Object v =
                        extractRecursive(item, path, idx);

                if (v != null) {
                    out.add(v);
                }
            }

            return out.isEmpty() ? null : out;
        }

        if (obj instanceof Map<?, ?> m) {
            List<Object> out =
                    new ArrayList<>();

            for (Object v : m.values()) {
                Object vv =
                        extractRecursive(v, path, idx);

                if (vv != null) {
                    out.add(vv);
                }
            }

            return out.isEmpty() ? null : out;
        }

        String part =
                path.get(idx);

        if ("name".equals(part) && obj instanceof Viewable q) {
            return q.getName();
        }

        // Read one level through the ONE FieldSet bridge (#87): a WDO's map-held field
        // (e.g. `won`) or a declared Java field, behind one interface, no `instanceof
        // DynamicFields` fork. has() (vs a present-null value) mirrors the old
        // containsKey guard so an absent field still returns null.
        if (obj instanceof Viewable q) {
            objectview.field.FieldSet fs = objectview.field.FieldSet.of(q);
            if (fs.has(part)) {
                return extractRecursive(fs.read(part), path, idx + 1);
            }
            return null;
        }

        Field f =
                getFieldCached(obj.getClass(), part);

        if (f == null) {
            return null;
        }

        return extractRecursive(f.get(obj), path, idx + 1);
    }

    private Field getFieldCached(
            Class<?> cls,
            String name) {

        Map<String, Field> map =
                fieldCache.computeIfAbsent(
                        cls,
                        k -> new ConcurrentHashMap<>());

        return map.computeIfAbsent(
                name,
                key -> ViewableAdapter.getField(cls, key));
    }

    private String flattenForSearch(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Viewable q) {
            return q.getName();
        }

        if (value instanceof Collection<?> c) {
            StringBuilder sb =
                    new StringBuilder();

            for (Object item : c) {
                sb.append(flattenForSearch(item))
                  .append(' ');
            }

            return sb.toString();
        }

        if (value instanceof Map<?, ?> m) {
            StringBuilder sb =
                    new StringBuilder();

            for (Object item : m.values()) {
                sb.append(flattenForSearch(item))
                  .append(' ');
            }

            return sb.toString();
        }

        return String.valueOf(value);
    }

    private boolean containsAllTokens(
            String haystack,
            List<String> tokens) {

        if (haystack == null) {
            return false;
        }

        for (String token : tokens) {
            if (!haystack.contains(token)) {
                return false;
            }
        }

        return true;
    }

    private String sortKey(Field leafField, Object value) {
        return isNumericField(leafField)
                ? numericSortKey(value)
                : sortableString(value);
    }

    private static boolean isNumericField(Field field) {
        return field != null
                && field.isAnnotationPresent(Numeric.class);
    }

    private static final java.util.regex.Pattern LEADING_NUMBER =
            java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?");

    // Leading number of the value's text, as a fixed-width offset key so
    // lexicographic order == numeric order (for |x| < 1e12).
    private String numericSortKey(Object value) {
        Double n = leadingNumber(value);
        return n == null ? "" : String.format("%026.6f", n + 1e12);
    }

    private Double leadingNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        if (value instanceof Collection<?> c) {
            for (Object o : c) {
                Double d = leadingNumber(o);
                if (d != null) {
                    return d;
                }
            }
            return null;
        }
        java.util.regex.Matcher m =
                LEADING_NUMBER.matcher(String.valueOf(value).trim());
        return m.lookingAt() ? Double.valueOf(m.group()) : null;
    }

    private String sortableString(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Viewable q) {
            return normalize(q.getName());
        }

        if (value instanceof Collection<?> c) {
            return c.stream()
                    .map(this::toSortable)
                    .filter(s -> !s.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .findFirst()
                    .orElse("");
        }

        if (value instanceof Map<?, ?> m) {
            return m.values().stream()
                    .map(this::toSortable)
                    .filter(s -> !s.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .findFirst()
                    .orElse("");
        }

        return normalize(String.valueOf(value));
    }

    private String toSortable(Object o) {
        if (o == null) {
            return "";
        }
        return o instanceof Viewable q
                ? normalize(q.getName())
                : normalize(String.valueOf(o));
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    private record SearchEntry(
            Card panel,
            Map<String, String> fieldTextByTitle) {
    }

    private record PanelSortKey(
            Card panel,
            String key) {
    }
}
