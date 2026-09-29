package objectview.search;

import objectview.ViewableAdapter;
import objectview.annotations.Hidden;
import objectview.annotations.Inline;
import objectview.field.ViewableFieldPaths;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forcing rule: <b>text a card paints is text search can find, and text it hides is
 * not findable.</b>
 *
 * <p>Search and the card renderer are two consumers of one field enumeration, and they
 * silently disagreed: the card descends into inline nesting and painted every query log
 * request, while the index collapsed each nested object to its name, so searching for a
 * QID inside a request matched nothing. Nothing failed — the view simply knew things the
 * index did not. These tests fail if that gap reopens, in either direction: search must
 * not stop above what is rendered, and must not reach past it into {@code @Hidden} data.
 */
class NestedValueSearchTest {

    /** A row that owns rows: one level per query group, as a log tree nests. */
    static class Step extends ViewableAdapter {
        String name;
        String request;

        @Hidden
        String internalToken = "never-rendered";

        @Inline
        Collection<Step> steps = new ArrayList<>();

        Step other; // a reference: painted as a name chip, not expanded

        Step(String name, String request) {
            this.name = name;
            this.request = request;
        }

        Step with(Step child) {
            steps.add(child);
            return this;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static List<ViewableFieldPaths.PathInfo> paths() {
        return ViewableFieldPaths.collect(
                ViewConfig.of(Step.class), ViewableFieldPaths.NOT_MEDIA_FIELDS);
    }

    /** Hits by the label the reader sees, over a search identified by field path.
     *  Two paths can share a label; this test's do not, and the result is asserted
     *  by label because that is what the reader is told to open. */
    private static Map<String, List<objectview.Viewable>> find(
            List<objectview.Viewable> rows, String query) {
        Map<String, List<objectview.Viewable>> byLabel = new java.util.LinkedHashMap<>();
        new SearchAndSort()
                .searchViewablesByPath(rows, List.of(query), paths(), false)
                .forEach((path, hits) -> byLabel
                        .computeIfAbsent(path.title(), ignored -> new java.util.ArrayList<>())
                        .addAll(hits));
        return byLabel;
    }

    private static Step tree() {
        return new Step("Generate domain", null)
                .with(new Step("Acquire statements", null)
                        .with(new Step("wbgetentities 50 entities",
                                "https://www.wikidata.org/w/api.php?ids=q42&props=claims")));
    }

    /** Navigation routes into a match the way the card draws it. A reference is a chip,
     *  so a hit on it addresses the chip even when the referent's own fields hold the
     *  same text; routing into those fields named a path nothing renders. An inline
     *  collection is drawn open, so its hit still routes down to the request row. */
    @Test
    void aHitOnAReferenceAddressesItsChipWhileAnInlineHitAddressesItsRow() {
        Step chip = new Step("Q42 answer", "Q42 in a request the chip does not draw");
        Step root = new Step("Generate domain", null)
                .with(new Step("Acquire", null)
                        .with(new Step("Request", "ids=Q29971182")));
        root.other = chip;
        SearchAndSort search = new SearchAndSort();

        List<SearchAndSort.ValueMatch> onReference = search.matchingValues(
                root, path("other"), List.of("q42"), false);
        assertEquals(1, onReference.size());
        assertEquals(objectview.field.FieldPath.of("other"),
                onReference.get(0).renderedPath());
        assertEquals(List.of(chip), onReference.get(0).collectionMembers());

        List<SearchAndSort.ValueMatch> inline = search.matchingValues(
                root, path("steps"), List.of("q29971182"), false);
        assertEquals(1, inline.size());
        assertEquals("request", inline.get(0).renderedPath().leaf(),
                "an inline hit still routes to the row that paints it");
    }

    private static ViewableFieldPaths.PathInfo path(String name) {
        return paths().stream()
                .filter(info -> info.path().equals(objectview.field.FieldPath.of(name)))
                .findFirst().orElseThrow();
    }

    @Test
    void findsTextOnAnInlineRowSeveralLevelsBelowTheCard() {
        Step root = tree();

        assertFalse(find(List.of(root), "props=claims").isEmpty(),
                "a request painted three levels down must be findable");
    }

    @Test
    void deepMatchIsReportedUnderTheFieldThatOwnsIt() {
        Step root = tree();

        assertEquals(List.of("steps"),
                List.copyOf(find(List.of(root), "props=claims").keySet()),
                "the hit belongs to the field the reader would open");
    }

    @Test
    void doesNotFindWhatTheCardNeverShows() {
        Step root = tree();

        assertTrue(find(List.of(root), "never-rendered").isEmpty(),
                "@Hidden is absent from the FieldSet, so search cannot reach it");
    }

    @Test
    void aReferenceContributesItsNameOnly() {
        Step root = new Step("run", null);
        root.other = new Step("other run", "secret-request-text");

        assertFalse(find(List.of(root), "other run").isEmpty(),
                "the chip's text is what the card shows for a reference");
        assertTrue(find(List.of(root), "secret-request-text").isEmpty(),
                "a reference is not expanded on the card, so it is not expanded here");
    }

    @Test
    void ordersByTheNameShownOnTheRowNotByTextNestedInsideIt() {
        // 'b' sorts after 'a' by name, while its nested request sorts first — so an
        // order driven by nested text would swap them.
        Step a = new Step("a", null).with(new Step("zzz child", "zzz"));
        Step b = new Step("b", null).with(new Step("aaa child", "aaa"));

        List<objectview.Viewable> sorted = new SearchAndSort().sortViewables(
                List.of(b, a),
                ViewableFieldPaths.collect(
                        nameOnly(), ViewableFieldPaths.NOT_MEDIA_FIELDS));

        assertEquals(List.of("a", "b"), sorted.stream().map(q -> q.getName()).toList());
    }

    @Test
    void aMapIsSearchedByKeyAndValueButIdentifiedByItsValues() {
        Map<String, String> facts = Map.of("symbol", "H");

        assertTrue(objectview.field.ValueText.shown(facts, 0)
                        .containsAll(List.of("symbol", "H")),
                "both halves of a map row are painted, so both are searchable");
        assertEquals(List.of("H"), objectview.field.ValueText.identity(facts),
                "a key labels a value; the row is ordered by the value");
    }

    @Test
    void aCycleTerminates() {
        Step root = new Step("run", null);
        Step child = new Step("step", "cyclic-request");
        child.steps.add(root); // back to the top
        root.steps.add(child);

        assertFalse(find(List.of(root), "cyclic-request").isEmpty(),
                "a graph that points back at itself is walked once, not forever");
    }

    private static ViewConfig nameOnly() {
        ViewConfig config = ViewConfig.of(Step.class);
        config.setAllFields(false);
        config.addField("name", ViewConfig.leaf());
        return config;
    }
}
