package objectview.search;

import objectview.ViewableAdapter;
import objectview.field.FieldPath;
import objectview.field.ViewableFieldPaths;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexedViewableSearchTest {

    static final class CountingItem extends ViewableAdapter {
        String searchableName;

        CountingItem(String name) { this.searchableName = name; }

        @Override public String getIdentifier() { return searchableName; }
        @Override public String getDisplayName() { return searchableName; }
    }

    @Test void aChangedScopeReadsOnlyWhatItHasNotReadBefore() {
        CountingItem king = new CountingItem("King of France");
        CountingItem writer = new CountingItem("Court poet");
        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                ViewConfig.of(CountingItem.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
        SearchAndSort search = new SearchAndSort();

        search.indexViewables(List.of(king), paths);
        long afterFirst = search.viewableSearchIndexRevision();
        search.indexViewables(List.of(king, writer), paths);
        long afterSecond = search.viewableSearchIndexRevision();
        search.indexViewables(List.of(writer, king), paths);

        assertEquals(afterSecond, search.viewableSearchIndexRevision(),
                "an item already read is not read again when the shown set changes");
        assertEquals(afterFirst + 1, afterSecond, "only the new item was read");

        // Scope is what the reader is looking at, and it is asked at search time.
        assertEquals(List.of(king), distinct(search.searchIndexedViewables(
                List.of("o"), false, paths, java.util.Set.of(king))));
        assertEquals(List.of(king, writer), distinct(search.searchIndexedViewables(
                List.of("o"), false, paths, null)));
    }

    @Test void repeatedSubstringSearchesUseTheIndexNotTheObjectGraph() {
        CountingItem apostolic = new CountingItem("Apostolic King of Hungary");
        CountingItem minister = new CountingItem("Minister of Finance");
        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                ViewConfig.of(CountingItem.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
        SearchAndSort search = new SearchAndSort();

        search.indexViewables(List.of(apostolic, minister), paths);
        apostolic.searchableName = "Changed after indexing";

        assertEquals(List.of(apostolic), distinct(search.searchIndexedViewables(List.of("apostolic"), false, paths, null)));
        assertEquals(List.of(minister), distinct(search.searchIndexedViewables(List.of("finance"), false, paths, null)));
        assertEquals(List.of(), distinct(search.searchIndexedViewables(List.of("changed"), false, paths, null)),
                "typing another query must not traverse fields again");
    }

    @Test void repeatedSearchRemainsInteractiveAtOneHundredThousandRows() {
        java.util.ArrayList<objectview.Viewable> rows = new java.util.ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++) {
            rows.add(new CountingItem(i == 72_341
                    ? "Apostolic King of Hungary" : "Position " + i));
        }
        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                ViewConfig.of(CountingItem.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
        SearchAndSort search = new SearchAndSort();
        search.indexViewables(rows, paths);

        assertTimeout(Duration.ofSeconds(2), () -> {
            for (int i = 0; i < 50; i++) {
                assertEquals(1, distinct(search.searchIndexedViewables(List.of("apostolic"), false, paths, null)).size());
            }
        });
    }

    @Test void everySubstringOfAnIndexedValueStillFindsIt() {
        // The property any narrowing must preserve. A filter that over-collects is
        // harmless, because matches() decides; one that drops a row is silent and
        // unfindable. Trigram postings were tried here and removed — 82 MB on a
        // million rows to turn a 30 ms scan into 8 ms — and this is what would have
        // to keep passing if anything like them is tried again.
        List<CountingItem> rows = List.of(
                new CountingItem("Apostolic King of Hungary"),
                new CountingItem("Kingdom of Hungary"),
                new CountingItem("mayor of a place in France"),
                new CountingItem("ma"));
        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                ViewConfig.of(CountingItem.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
        SearchAndSort search = new SearchAndSort();
        search.indexViewables(List.copyOf(rows), paths);

        for (CountingItem row : rows) {
            String text = row.searchableName.toLowerCase(java.util.Locale.ROOT);
            for (int from = 0; from < text.length(); from++) {
                for (int to = from + 1; to <= text.length(); to++) {
                    String query = text.substring(from, to);
                    assertTrue(distinct(search.searchIndexedViewables(
                                    List.of(query), false, paths, null)).contains(row),
                            "'" + query + "' must still find '" + text + "'");
                }
            }
        }
    }

    @Test void aPhraseIsOneContinuousSubstring() {
        CountingItem wanted = new CountingItem("King of Hungary");
        CountingItem falsePositive = new CountingItem("Kingdom of Hungary");
        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                ViewConfig.of(CountingItem.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
        SearchAndSort search = new SearchAndSort();
        search.indexViewables(List.of(wanted, falsePositive), paths);

        assertEquals(List.of(wanted), distinct(search.searchIndexedViewables(List.of("king of"), false, paths, null)));
    }

    @Test void pathsWithTheSameLabelRemainDistinctIndexEntries() throws Exception {
        DualItem item = new DualItem("needle on left", "needle on right");
        ViewableFieldPaths.PathInfo left = new ViewableFieldPaths.PathInfo(
                "Same label", FieldPath.of("left"), DualItem.class.getDeclaredField("left"));
        ViewableFieldPaths.PathInfo right = new ViewableFieldPaths.PathInfo(
                "Same label", FieldPath.of("right"), DualItem.class.getDeclaredField("right"));
        SearchAndSort search = new SearchAndSort();

        search.indexViewables(List.of(item), List.of(left, right));
        var matches = search.searchIndexedViewables(
                List.of("needle"), false, List.of(left, right), null);

        assertEquals(List.of(left, right), List.copyOf(matches.keySet()),
                "a display label must not be the identity of a field path");
        assertEquals(List.of(item), matches.get(left));
        assertEquals(List.of(item), matches.get(right));
    }

    private static List<objectview.Viewable> distinct(
            java.util.Map<?, List<objectview.Viewable>> result) {
        return result.values().stream().flatMap(List::stream).distinct().toList();
    }

    static final class DualItem extends ViewableAdapter {
        String left;
        String right;
        DualItem(String left, String right) {
            this.left = left;
            this.right = right;
        }
        @Override public String getIdentifier() { return left + right; }
        @Override public String getDisplayName() { return left; }
    }
}
