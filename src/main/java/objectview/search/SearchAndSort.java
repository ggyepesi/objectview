package objectview.search;

import objectview.field.ValueText;
import objectview.field.ViewableFieldPaths;
import objectview.field.FieldPath;

import objectview.annotations.Numeric;
import objectview.render.Card;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.function.Function;

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
    /** The read text, per searched path, with what has already been read for it. */
    private final Map<FieldPath, PathIndex> viewableIndex = new LinkedHashMap<>();
    private long viewableSearchIndexRevision;
    private Function<objectview.Viewable, objectview.field.FieldSchema>
            fieldSchemaResolver = ignored -> null;

    /** Installs the same schema source used by rendering. Changing it invalidates
     * every extracted value because nested path interpretation may have changed. */
    public void setFieldSchemaResolver(
            Function<objectview.Viewable, objectview.field.FieldSchema> resolver) {
        fieldSchemaResolver = resolver == null ? ignored -> null : resolver;
        searchIndex.clear();
        clearViewableSearchIndex();
    }

    public void rebuildSearchIndex(
            JComponent targetPanel,
            List<ViewableFieldPaths.PathInfo> paths) {

        searchIndex.clear();

        if (targetPanel == null || paths == null) {
            return;
        }
        Function<objectview.Viewable, objectview.field.FieldSchema> schemas =
                batchSchemaResolver();

        for (Component c : targetPanel.getComponents()) {
            if (!(c instanceof Card qp)) {
                continue;
            }

            Map<ViewableFieldPaths.PathInfo, SearchText> fieldTextByPath =
                    new LinkedHashMap<>();

            for (ViewableFieldPaths.PathInfo fp : paths) {
                Object value =
                        extractValue(qp.getViewable(), fp.path(), schemas);

                fieldTextByPath.put(
                        fp,
                        searchText(fp, value));
            }

            searchIndex.add(new SearchEntry(qp, fieldTextByPath));
        }
    }

    /** Component search, identified by access path: a label is presentation and two
     *  fields may share one, which is why hits are not keyed by it. */
    public Map<ViewableFieldPaths.PathInfo, List<Card>> searchByPath(
            List<String> queryTokens, boolean exact) {

        Map<ViewableFieldPaths.PathInfo, List<Card>> out =
                new LinkedHashMap<>();

        if (queryTokens == null || queryTokens.isEmpty()) {
            return out;
        }

        for (SearchEntry entry : searchIndex) {
            for (Map.Entry<ViewableFieldPaths.PathInfo, SearchText> field
                    : entry.fieldTextByPath.entrySet()) {

                if (!matches(field.getValue(), queryTokens, exact)) {
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
     *  matching viewables per field, in field-then-data order. The caller navigates
     *  these hits one at a time, building each card on demand.
     *
     *  <p>Uncached, and identified by access path — a label is what the reader is
     *  shown, and two different fields may be shown the same one. Keying hits by
     *  label merged unrelated fields into one bucket, which is the bug this replaced.
     */
    public Map<ViewableFieldPaths.PathInfo, List<objectview.Viewable>> searchViewablesByPath(
            List<objectview.Viewable> viewables,
            List<String> queryTokens,
            List<ViewableFieldPaths.PathInfo> paths,
            boolean exact) {

        Map<ViewableFieldPaths.PathInfo, List<objectview.Viewable>> out =
                new LinkedHashMap<>();

        if (viewables == null
                || viewables.isEmpty()
                || paths == null
                || queryTokens == null
                || queryTokens.isEmpty()) {
            return out;
        }
        Function<objectview.Viewable, objectview.field.FieldSchema> schemas =
                batchSchemaResolver();

        for (ViewableFieldPaths.PathInfo fp : paths) {
            List<objectview.Viewable> hits = null;

            for (objectview.Viewable q : viewables) {
                Object value =
                        extractValue(q, fp.path(), schemas);

                if (matches(searchText(fp, value), queryTokens, exact)) {

                    if (hits == null) {
                        hits = new ArrayList<>();
                    }

                    hits.add(q);
                }
            }

            if (hits != null) {
                out.put(fp, hits);
            }
        }

        return out;
    }

    /**
     * Reads the shown text of each value once and keeps it.
     *
     * <p>Called again with a changed item set, it extracts only what it has not seen:
     * the text of a value depends on the value and the path, not on which group is
     * being shown, so switching scope must not re-read a million fields. Adding a
     * path indexes that path alone.
     *
     * <p>There is no trigram index. Postings over 3-character windows were measured
     * here on a million rows: 82 MB to turn a 30 ms scan into 8 ms. The dictionary is
     * not what costs — this text has about 1 165 distinct trigrams — it is that each
     * one addresses roughly 11 700 rows, so the postings are 13.6 million row numbers
     * and every query ANDs million-bit vectors to become selective. The same text
     * holds 54 477 distinct WORDS at about 12 rows each; a term index is the shape
     * that would pay, and it answers a different question (prefix and phrase, never
     * an infix), so it is a decision about what search means and not an optimisation.
     */
    public void indexViewables(
            List<objectview.Viewable> viewables,
            List<ViewableFieldPaths.PathInfo> paths) {
        applyIndex(extractIndex(planIndex(viewables, paths, Set.of())));
    }

    /**
     * Describes exactly which field values are missing or explicitly changed.
     * Planning touches only index identities; reflective extraction happens later,
     * and can therefore run away from the Swing event thread.
     */
    IndexWork planIndex(
            List<objectview.Viewable> viewables,
            List<ViewableFieldPaths.PathInfo> paths,
            Set<objectview.Viewable> changed) {
        List<PathWork> pathWork = new ArrayList<>();
        int size = 0;
        if (viewables == null || paths == null) return new IndexWork(pathWork, size);
        for (ViewableFieldPaths.PathInfo fp : paths) {
            if (fp == null || fp.path() == null) continue;
            PathIndex index = viewableIndex.get(fp.path());
            List<objectview.Viewable> pending = new ArrayList<>();
            for (objectview.Viewable viewable : viewables) {
                if (viewable == null) continue;
                boolean refresh = changed != null && changed.contains(viewable);
                if (refresh || index == null || !index.rows.containsKey(viewable)) {
                    pending.add(viewable);
                }
            }
            if (!pending.isEmpty()) {
                pathWork.add(new PathWork(fp, pending));
                size += pending.size();
            }
        }
        return new IndexWork(pathWork, size);
    }

    /** Pure, unpublished extraction result; safe to compute on a worker thread. */
    IndexDelta extractIndex(IndexWork work) {
        List<ExtractedPath> extracted = new ArrayList<>();
        if (work == null) return new IndexDelta(extracted);
        Function<objectview.Viewable, objectview.field.FieldSchema> schemas =
                batchSchemaResolver();
        for (PathWork pathWork : work.paths()) {
            List<SearchText> texts = new ArrayList<>(pathWork.viewables().size());
            for (objectview.Viewable viewable : pathWork.viewables()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException();
                }
                texts.add(searchText(pathWork.path(),
                        extractValue(viewable, pathWork.path().path(), schemas)));
            }
            extracted.add(new ExtractedPath(
                    pathWork.path(), pathWork.viewables(), texts));
        }
        return new IndexDelta(extracted);
    }

    /** Publishes a completed extraction atomically from the caller's perspective. */
    void applyIndex(IndexDelta delta) {
        if (delta == null || delta.paths().isEmpty()) return;
        for (ExtractedPath extracted : delta.paths()) {
            PathIndex index = viewableIndex.computeIfAbsent(
                    extracted.path().path(), ignored -> new PathIndex());
            for (int i = 0; i < extracted.viewables().size(); i++) {
                objectview.Viewable viewable = extracted.viewables().get(i);
                SearchText text = extracted.texts().get(i);
                Integer row = index.rows.get(viewable);
                if (row == null) {
                    index.rows.put(viewable, index.items.size());
                    index.items.add(viewable);
                    index.texts.add(text);
                } else {
                    index.texts.set(row, text);
                }
            }
        }
        viewableSearchIndexRevision++;
    }

    /** Forgets everything read for a target that is being replaced. */
    public void clearViewableSearchIndex() {
        viewableIndex.clear();
    }

    /** Monotonic diagnostic revision; it advances only when a value was actually read. */
    long viewableSearchIndexRevision() {
        return viewableSearchIndexRevision;
    }

    /**
     * Hits per field path, over the paths asked for and the items in scope.
     *
     * <p>Scope is what the reader is looking at now. It is a set held by the caller
     * rather than a mark on the instance: a Viewable is domain data that several
     * views may show at once, and a "currently shown" flag on it would belong to
     * whichever of them rendered last.
     */
    public Map<ViewableFieldPaths.PathInfo, List<objectview.Viewable>> searchIndexedViewables(
            List<String> queryTokens, boolean exact,
            List<ViewableFieldPaths.PathInfo> paths,
            Set<objectview.Viewable> scope) {
        Map<ViewableFieldPaths.PathInfo, List<objectview.Viewable>> out =
                new LinkedHashMap<>();
        if (queryTokens == null || queryTokens.isEmpty() || paths == null) return out;

        for (ViewableFieldPaths.PathInfo fp : paths) {
            PathIndex index = viewableIndex.get(fp.path());
            if (index == null) continue;
            List<objectview.Viewable> hits = null;
            for (int row = 0; row < index.items.size(); row++) {
                objectview.Viewable item = index.items.get(row);
                if (scope != null && !scope.contains(item)) continue;
                if (!matches(index.texts.get(row), queryTokens, exact)) continue;
                if (hits == null) hits = out.computeIfAbsent(
                        fp, ignored -> new ArrayList<>());
                hits.add(item);
            }
        }
        return out;
    }

    public List<Card> sortPanels(
            List<Card> panels,
            List<ViewableFieldPaths.PathInfo> sortPaths) {

        List<PanelSortKey> keyed =
                new ArrayList<>();
        Function<objectview.Viewable, objectview.field.FieldSchema> schemas =
                batchSchemaResolver();

        for (Card panel : panels) {
            keyed.add(new PanelSortKey(
                    panel,
                    buildSortKey(panel, sortPaths, schemas)));
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
            List<ViewableFieldPaths.PathInfo> paths,
            Function<objectview.Viewable, objectview.field.FieldSchema> schemas) {

        StringBuilder sb =
                new StringBuilder();

        for (ViewableFieldPaths.PathInfo f : paths) {
            Object value =
                    extractValue(panel.getViewable(), f.path(), schemas);

            // A @Numeric leaf field sorts by its leading number ("1538 K" ->
            // 1538), not lexically — driven by the annotation, not the value type.
            sb.append(sortKey(f, value))
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
            List<ViewableFieldPaths.PathInfo> sortPaths) {

        List<objectview.Viewable> out = new ArrayList<>(viewables);
        Function<objectview.Viewable, objectview.field.FieldSchema> schemas =
                batchSchemaResolver();
        out.sort(Comparator.comparing(q -> buildSortKeyQ(q, sortPaths, schemas)));
        return out;
    }

    private String buildSortKeyQ(
            objectview.Viewable viewable,
            List<ViewableFieldPaths.PathInfo> paths,
            Function<objectview.Viewable, objectview.field.FieldSchema> schemas) {

        StringBuilder sb = new StringBuilder();
        for (ViewableFieldPaths.PathInfo f : paths) {
            Object value = extractValue(viewable, f.path(), schemas);
            sb.append(sortKey(f, value)).append((char) 0);
        }
        sb.append(sortableString(viewable));
        return sb.toString();
    }

    private Object extractValue(
            Object obj,
            FieldPath path,
            Function<objectview.Viewable, objectview.field.FieldSchema> schemas) {
        try {
            return objectview.field.FieldAccess.getPathValues(obj, path, schemas);
        } catch (ConcurrentModificationException movedUnderneath) {
            throw movedUnderneath;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * One lookup per INSTANCE for an extraction/sort batch, and never fewer.
     *
     * <p>A resolver takes a Viewable, and at least one real implementation means it:
     * TransformApp answers through the instance's most specific class, read from the
     * stamps that instance carries. Two objects sharing a type name genuinely differ
     * there — the Oscars snapshot holds both ('Person') and ('ForWork','Person')
     * under the type name Person — so caching by type name would hand the first
     * one's schema to the rest, and a nested path would then be read against a shape
     * the value does not have. Silently.
     *
     * <p>Per instance is also where the repetition actually is: one instance is read
     * once per searched path and again for every Viewable reached along it, so this
     * removes the calls that repeat while keeping the answer the resolver gave.
     */
    private Function<objectview.Viewable, objectview.field.FieldSchema>
    batchSchemaResolver() {
        Function<objectview.Viewable, objectview.field.FieldSchema> source =
                fieldSchemaResolver;
        Map<objectview.Viewable, Optional<objectview.field.FieldSchema>> byInstance =
                new IdentityHashMap<>();
        return viewable -> viewable == null ? null
                : byInstance.computeIfAbsent(viewable,
                        instance -> Optional.ofNullable(source.apply(instance)))
                        .orElse(null);
    }

    /** Everything the field shows, nested objects included — what a reader can SEE on
     *  the card is what they can find. {@link ValueText} owns the traversal; this owns
     *  only how the pieces are joined into one haystack.
     *
     *  <p>An INLINE field is descended into because the card paints it that way; a
     *  reference renders as a name chip and is read as one, at the top of a path exactly
     *  as inside it. A dynamic field has no declared Java field to ask, and reads as a
     *  reference. */
    private SearchText searchText(
            ViewableFieldPaths.PathInfo field, Object value) {
        int depth = objectview.ViewableAdapter.isInline(field.leafField())
                ? ValueText.NESTED_DEPTH : 0;
        List<String> atoms = ValueText.shown(value, depth).stream()
                .map(this::normalize).filter(s -> !s.isBlank()).toList();
        // One atom IS the haystack. Joining a single-element list copies its
        // characters into an equal String, and an index over a loaded domain keeps
        // one of those per row — most fields hold a single value, so that copy was
        // a second set of every string the domain shows.
        return new SearchText(atoms.size() == 1 ? atoms.get(0)
                : String.join(" ", atoms), atoms);
    }

    private boolean matches(SearchText text, List<String> tokens, boolean exact) {
        if (text == null || tokens == null || tokens.isEmpty()) return false;
        if (!exact) return containsAllTokens(text.flattened(), tokens);
        String wanted = String.join(" ", tokens);
        return text.atoms().stream().anyMatch(wanted::equals);
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

    private String sortKey(ViewableFieldPaths.PathInfo field, Object value) {
        // Numeric by the declared kind (ORDERED — covers a persisted @Numeric on a
        // dynamic/snapshot field with no reflection Field) OR by the reflected field.
        boolean numeric = field.valueKind() == objectview.field.FieldKind.ORDERED
                || isNumericField(field.leafField());
        return numeric ? numericSortKey(value) : sortableString(value);
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

    // The numeric value of the field, as a fixed-width offset key so lexicographic order
    // == numeric order (for |x| < 1e12). Uses the ONE shared numeric reading
    // (NumericValues) so a scaled/ranged string sorts the same way it orders and filters.
    private String numericSortKey(Object value) {
        Double n = leadingNumber(value);
        return n == null ? "" : String.format("%026.6f", n + 1e12);
    }

    private Double leadingNumber(Object value) {
        if (value instanceof Collection<?> c) {
            for (Object o : c) {
                Double d = leadingNumber(o);
                if (d != null) {
                    return d;
                }
            }
            return null;
        }
        java.util.OptionalDouble n = objectview.field.NumericValues.parse(value);
        return n.isPresent() ? n.getAsDouble() : null;
    }

    /** What the value is IDENTIFIED by, ordered: the same traversal as the search
     *  haystack, stopped at depth 0 so a nested object reads as its name — a row orders
     *  by the chip it displays, not by text hidden inside the object it points at. A
     *  many-valued field orders by its first value. */
    private String sortableString(Object value) {
        return ValueText.identity(value).stream()
                .map(this::normalize)
                .filter(s -> !s.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .findFirst()
                .orElse("");
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    private record SearchEntry(
            Card panel,
            Map<ViewableFieldPaths.PathInfo, SearchText> fieldTextByPath) {
    }

    /** One searched path: the items read for it, their text, and which are done. */
    private static final class PathIndex {
        private final List<objectview.Viewable> items = new ArrayList<>();
        private final List<SearchText> texts = new ArrayList<>();
        private final Map<objectview.Viewable, Integer> rows =
                new java.util.IdentityHashMap<>();
    }

    record PathWork(
            ViewableFieldPaths.PathInfo path,
            List<objectview.Viewable> viewables) {
        PathWork { viewables = List.copyOf(viewables); }
    }

    record IndexWork(List<PathWork> paths, int size) {
        IndexWork { paths = paths == null ? List.of() : List.copyOf(paths); }
    }

    record ExtractedPath(
            ViewableFieldPaths.PathInfo path,
            List<objectview.Viewable> viewables,
            List<SearchText> texts) {
        ExtractedPath {
            viewables = List.copyOf(viewables);
            texts = List.copyOf(texts);
        }
    }

    record IndexDelta(List<ExtractedPath> paths) {
        IndexDelta { paths = paths == null ? List.of() : List.copyOf(paths); }
        Set<objectview.Viewable> viewables() {
            Set<objectview.Viewable> out = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<>());
            for (ExtractedPath path : paths) out.addAll(path.viewables());
            return out;
        }
    }

    private record SearchText(String flattened, List<String> atoms) {
        private SearchText {
            flattened = flattened == null ? "" : flattened;
            atoms = atoms == null ? List.of() : List.copyOf(atoms);
        }
    }

    private record PanelSortKey(
            Card panel,
            String key) {
    }
}
