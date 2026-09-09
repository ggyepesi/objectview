package objectview.search;

import objectview.ViewableAdapter;
import objectview.field.ViewableFieldPaths;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class IndexedViewableSearchTest {

    static final class CountingItem extends ViewableAdapter {
        String searchableName;

        CountingItem(String name) { this.searchableName = name; }

        @Override public String getIdentifier() { return searchableName; }
        @Override public String getDisplayName() { return searchableName; }
    }

    @Test void repeatedSubstringSearchesUseTheIndexNotTheObjectGraph() {
        CountingItem apostolic = new CountingItem("Apostolic King of Hungary");
        CountingItem minister = new CountingItem("Minister of Finance");
        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                ViewConfig.of(CountingItem.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
        SearchAndSort search = new SearchAndSort();

        search.rebuildViewableSearchIndex(List.of(apostolic, minister), paths);
        apostolic.searchableName = "Changed after indexing";

        assertEquals(List.of(apostolic), distinct(search.searchIndexedViewables(
                List.of("apostolic"), false)));
        assertEquals(List.of(minister), distinct(search.searchIndexedViewables(
                List.of("finance"), false)));
        assertEquals(List.of(), distinct(search.searchIndexedViewables(
                List.of("changed"), false)),
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
        search.rebuildViewableSearchIndex(rows, paths);

        assertTimeout(Duration.ofSeconds(2), () -> {
            for (int i = 0; i < 50; i++) {
                assertEquals(1, distinct(search.searchIndexedViewables(
                        List.of("apostolic"), false)).size());
            }
        });
    }

    @Test void aPhraseIsOneContinuousSubstring() {
        CountingItem wanted = new CountingItem("King of Hungary");
        CountingItem falsePositive = new CountingItem("Kingdom of Hungary");
        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                ViewConfig.of(CountingItem.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
        SearchAndSort search = new SearchAndSort();
        search.rebuildViewableSearchIndex(List.of(wanted, falsePositive), paths);

        assertEquals(List.of(wanted), distinct(search.searchIndexedViewables(
                List.of("king of"), false)));
    }

    private static List<objectview.Viewable> distinct(
            java.util.Map<String, List<objectview.Viewable>> result) {
        return result.values().stream().flatMap(List::stream).distinct().toList();
    }
}
