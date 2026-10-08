package objectview.search;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Reference;
import objectview.field.FieldPath;
import objectview.field.PathWalk;
import objectview.field.ViewableFieldPaths.PathInfo;
import objectview.plan.LiteralPaths;
import objectview.plan.TypeShape;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Search and sort read a selection through the fields that inherit an ancestor's config
 * (#368). A search over a position's name also finds the positions it replaced, at any
 * distance; the walk enters each object once per level, so a cycle ends it. A sort
 * compares a path's values one by one in the order the path reads them — first value
 * decides, the next breaks a tie, a shorter run sorts first — for a field with several
 * values as for an inherited path. It used to reduce several values to one: the
 * alphabetically smallest text, or the first number.
 */
class InheritedSearchAndSortTest {

    static final class Office extends ViewableAdapter {
        @DisplayField private final String name;
        @Reference Office predecessor;
        @Reference final List<Office> replaces = new ArrayList<>();

        Office(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    static final class Film extends ViewableAdapter {
        @DisplayField private final String title;
        private final List<String> cast;
        private final List<Integer> years;

        Film(String title, List<String> cast, List<Integer> years) {
            this.title = title;
            this.cast = cast;
            this.years = years;
        }
        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }

    private final Office president = new Office("President");
    private final Office emperor = new Office("Emperor");
    private final Office king = new Office("King of Prussia");

    InheritedSearchAndSortTest() {
        president.predecessor = emperor;
        emperor.predecessor = king;
        president.replaces.add(emperor);
    }

    /** name, and the two recursive fields inheriting the root's selection. */
    private static PathInfo inheritedName() {
        ViewConfig root = ViewConfig.leaf();
        root.addField("name", ViewConfig.leaf());
        root.addField("predecessor", ViewConfig.inheriting(root));
        root.addField("replaces", ViewConfig.inheriting(root));
        List<PathInfo> paths = LiteralPaths.selection(root, TypeShape.ofClass(Office.class), true);
        assertEquals(List.of(FieldPath.of("name")), paths.stream().map(PathInfo::path).toList(),
                "an inheriting field adds no path of its own; it extends the walk");
        return paths.get(0);
    }

    @Test void theWalkReadsEveryLevelOnceInDepthFirstListOrder() {
        PathWalk walk = inheritedName().walk();
        assertNotNull(walk);

        List<PathWalk.Reached> reached = walk.read(president, value -> null);

        assertEquals(List.of("President", "Emperor", "King of Prussia"),
                reached.stream().map(r -> String.valueOf(r.value())).toList(),
                "the Emperor reached again through replaces is not read twice");
        assertEquals(List.of(FieldPath.of("name"), FieldPath.of("predecessor", "name"),
                        FieldPath.of("predecessor", "predecessor", "name")),
                reached.stream().map(PathWalk.Reached::rendered).toList());
    }

    @Test void anObjectAlreadyReachedEndsACycle() {
        king.predecessor = president;

        List<PathWalk.Reached> reached = inheritedName().walk().read(president, value -> null);

        assertEquals(3, reached.size());
    }

    @Test void aSearchFindsAValueSeveralInheritedLevelsDownAndRoutesToIt() {
        SearchAndSort search = new SearchAndSort();
        PathInfo name = inheritedName();

        assertEquals(List.of(president), search.searchViewablesByPath(
                List.of(president), List.of("prussia"), List.of(name), false).get(name));

        List<SearchAndSort.ValueMatch> matches =
                search.matchingValues(president, name, List.of("prussia"), false);
        assertEquals(1, matches.size());
        assertEquals(FieldPath.of("predecessor", "predecessor", "name"),
                matches.get(0).renderedPath());
        assertEquals(List.of(emperor, king), matches.get(0).collectionMembers(),
                "the objects to open on the way to the hit");
    }

    @Test void aNavigationOnlyScalarContributesItsCaptionButNothingBehindIt() {
        Office head = new Office("Head");
        Office middle = new Office("Middle");
        Office tail = new Office("Tail");
        head.predecessor = middle;
        middle.predecessor = tail;
        SearchAndSort search = new SearchAndSort();
        search.setTopLevelPredicate(value -> value == middle || value == tail);
        PathInfo name = inheritedName();

        assertEquals(List.of(head), search.searchViewablesByPath(
                List.of(head), List.of("middle"), List.of(name), false).get(name),
                "the visible navigation caption remains searchable");
        assertEquals(List.of(), search.searchViewablesByPath(
                        List.of(head), List.of("tail"), List.of(name), false)
                .getOrDefault(name, List.of()),
                "fields behind the navigation link cannot become hidden hits on Head");
    }

    @Test void aSelectionWithoutInheritanceReadsAlongItsPathsAsBefore() {
        ViewConfig root = ViewConfig.leaf();
        root.addField("name", ViewConfig.leaf());
        ViewConfig predecessor = ViewConfig.leaf();
        predecessor.addField("name", ViewConfig.leaf());
        root.addField("predecessor", predecessor);

        List<PathInfo> paths = LiteralPaths.selection(root, TypeShape.ofClass(Office.class), true);

        assertEquals(2, paths.size());
        paths.forEach(path -> assertNull(path.walk(), path.dotted()));
    }

    @Test void aSortReadsAnInheritedPathAsItsWholeLineage() {
        Office a = new Office("A");
        a.predecessor = new Office("C");
        Office b = new Office("A");
        b.predecessor = new Office("B");

        List<Viewable> sorted = new SearchAndSort().sortViewables(
                List.of(a, b), List.of(inheritedName()));

        assertEquals(List.of(b, a), sorted, "A·B before A·C: the second value breaks the tie");
    }

    @Test void severalValuesAreComparedOneByOneNotReducedToOne() {
        Film lateFirst = new Film("late", List.of("Brigitte Helm", "Alfred Abel"), List.of(1927));
        Film earlyFirst = new Film("early", List.of("Alfred Abel", "Rudolf Klein-Rogge"),
                List.of(1927));
        Film prefix = new Film("prefix", List.of("Alfred Abel"), List.of(1927));

        assertEquals(List.of("prefix", "early", "late"), sortedBy("cast",
                        lateFirst, earlyFirst, prefix),
                "the first value decides and a shorter run sorts first; the smallest-value "
                        + "rule tied all three on Alfred Abel");
    }

    @Test void numbersFollowTheSameRule() {
        Film first = new Film("first", List.of(), List.of(1931, 1927));
        Film second = new Film("second", List.of(), List.of(1927, 1940));
        Film third = new Film("third", List.of(), List.of(1927, 950));

        assertEquals(List.of("third", "second", "first"),
                sortedBy("years", first, second, third),
                "1927·950 < 1927·1940 < 1931·1927, compared as numbers, not as text");
    }

    private static List<String> sortedBy(String field, Film... films) {
        ViewConfig config = ViewConfig.leaf();
        config.addField(field, ViewConfig.leaf());
        List<PathInfo> paths = LiteralPaths.selection(config, TypeShape.ofClass(Film.class), true);
        return new SearchAndSort().sortViewables(List.of(films), paths).stream()
                .map(Viewable::getName).toList();
    }
}
