package objectview.search;

import objectview.Viewable;
import objectview.field.ViewableFieldPaths;

import objectview.annotations.Numeric;
import objectview.render.Card;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

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

    private final List<SearchEntry> searchIndex =
            new ArrayList<>();

    public void rebuildSearchIndex(
            JComponent targetPanel,
            List<ViewableFieldPaths.FieldPath> paths) {

        searchIndex.clear();

        if (targetPanel == null || paths == null) {
            return;
        }

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
            List<ViewableFieldPaths.FieldPath> paths) {

        Map<String, List<objectview.Viewable>> out =
                new LinkedHashMap<>();

        if (viewables == null
                || viewables.isEmpty()
                || paths == null
                || queryTokens == null
                || queryTokens.isEmpty()) {
            return out;
        }

        for (ViewableFieldPaths.FieldPath fp : paths) {
            List<objectview.Viewable> hits = null;

            for (objectview.Viewable q : viewables) {
                Object value =
                        extractValue(q, fp.path());

                if (containsAllTokens(
                        normalize(flattenForSearch(value)),
                        queryTokens)) {

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
            return objectview.field.FieldAccess.getPathValues(obj, path);
        } catch (RuntimeException ignored) {
            return null;
        }
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

            m.forEach((key, item) -> sb.append(flattenForSearch(key))
                    .append(' ')
                    .append(flattenForSearch(item))
                    .append(' '));

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
        if (field == null) {
            return false;
        }
        // @Numeric marks a String-backed (dynamic) field whose values are numbers; a field
        // whose Java type is already numeric needs no annotation to sort as a number — else
        // an int like a relation count sorts lexically ("100" before "20").
        if (field.isAnnotationPresent(Numeric.class)) {
            return true;
        }
        Class<?> type = field.getType();
        return type == int.class || type == long.class || type == short.class
                || type == byte.class || type == double.class || type == float.class
                || Number.class.isAssignableFrom(type);
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
