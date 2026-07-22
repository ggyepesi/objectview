package objectview.field;
import objectview.ViewableAdapter;

import objectview.annotations.Hidden;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;
import objectview.media.ImagePane;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ViewableFieldPathsTest {

    @Test
    void collectionOfStringIsLeafWhenSelected() {
        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("tags", ViewConfig.leaf());

        List<ViewableFieldPaths.FieldPath> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("tags"), pathStrings(paths));
    }

    @Test
    void collectionOfQuizableUsesNestedSelectedFieldsOnly() {
        ViewConfig childConfig = ViewConfig.of(TestChild.class);
        childConfig.setAllFields(false);
        childConfig.addField("name", ViewConfig.leaf());

        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("children", childConfig);

        List<ViewableFieldPaths.FieldPath> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("children.name"), pathStrings(paths));
    }

    @Test
    void collectFromSampleEmitsNameForAnUnconfiguredReferenceChild() {
        TestChild child = new TestChild();
        child.name = "Meryl";
        child.code = "42";
        TestCard card = new TestCard();
        card.name = "nom";
        card.children = List.of(child);

        // `children` is selected but with NO child selection -> only its display name
        // is a searchable path, not the child's other (unconfigured) fields.
        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("children", ViewConfig.leaf());

        Set<String> paths = pathStrings(ViewableFieldPaths.collectFromSample(
                card, config, ViewableFieldPaths.NOT_MEDIA_FIELDS));

        assertEquals(Set.of("children.name"), paths);
    }

    @Test
    void collectFromSampleRecursesIntoConfiguredChildFieldsOnly() {
        TestChild child = new TestChild();
        child.name = "Meryl";
        child.code = "42";
        TestCard card = new TestCard();
        card.name = "nom";
        card.children = List.of(child);

        // The child config selects `code` only -> children.code is enumerated, and the
        // unconfigured children.name is NOT. This is the recursive-config restriction.
        ViewConfig childConfig = ViewConfig.of(TestChild.class);
        childConfig.setAllFields(false);
        childConfig.addField("code", ViewConfig.leaf());
        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("children", childConfig);

        Set<String> paths = pathStrings(ViewableFieldPaths.collectFromSample(
                card, config, ViewableFieldPaths.NOT_MEDIA_FIELDS));

        assertEquals(Set.of("children.code"), paths);
    }

    @Test
    void imagePaneFieldsAreExcluded() {
        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("name", ViewConfig.leaf());
        config.addField("image", ViewConfig.leaf());

        List<ViewableFieldPaths.FieldPath> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("name"), pathStrings(paths));
    }

    @Test
    void recursiveTypeDoesNotOverflowWhenOnlyNameIsSelected() {
        ViewConfig config = ViewConfig.of(SelfNode.class);
        config.setAllFields(false);
        config.addField("name", ViewConfig.leaf());

        List<ViewableFieldPaths.FieldPath> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("name"), pathStrings(paths));
    }

    private Set<String> pathStrings(List<ViewableFieldPaths.FieldPath> paths) {
        return paths.stream()
                .map(p -> String.join(".", p.path()))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unused")
    private static class TestCard extends ViewableAdapter {
        private String name;
        private List<String> tags;
        private List<TestChild> children;
        private ImagePane image;

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    @SuppressWarnings("unused")
    private static class TestChild extends ViewableAdapter {
        private String name;
        private String code;

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    @SuppressWarnings("unused")
    private static class SelfNode extends ViewableAdapter {
        private String name;
        private List<SelfNode> children;

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    // Mirrors WikidataDynamicObject: identity fields hidden from the card, and a
    // bare reference has no other fields.
    @SuppressWarnings("unused")
    private static class EntityCard extends ViewableAdapter {
        @Hidden
        private String qid;
        @Hidden
        private String name;

        @Override public String getIdentifier() { return qid; }
        @Override public String getDisplayName() { return name; }
    }

    @Test
    void allFieldsImpliesIdentityExplicitConfigDoesNot() {
        // "All fields" implies the identity (name/qid). An EXPLICIT config means
        // exactly what it names — forcing name in regardless made search hit on
        // name even when the user unchecked it.
        ViewConfig all = ViewConfig.of(EntityCard.class);
        all.setAllFields(true);
        Set<String> allPaths = pathStrings(ViewableFieldPaths.collect(
                all, ViewableFieldPaths.NOT_MEDIA_FIELDS));
        assertTrue(allPaths.contains("name"), allPaths.toString());
        assertTrue(allPaths.contains("qid"), allPaths.toString());

        ViewConfig explicit = ViewConfig.of(EntityCard.class);
        explicit.setAllFields(false);
        Set<String> explicitPaths = pathStrings(ViewableFieldPaths.collect(
                explicit, ViewableFieldPaths.NOT_MEDIA_FIELDS));
        assertFalse(explicitPaths.contains("name"), explicitPaths.toString());
        assertFalse(explicitPaths.contains("qid"), explicitPaths.toString());
    }

    @Test
    void dedupByPathKeepsFirstOfEachDistinctPath() {
        ViewableFieldPaths.FieldPath name =
                new ViewableFieldPaths.FieldPath("name", List.of("name"), null);
        ViewableFieldPaths.FieldPath nameAgain =
                new ViewableFieldPaths.FieldPath("name (dup)", List.of("name"), null);
        ViewableFieldPaths.FieldPath code =
                new ViewableFieldPaths.FieldPath("code", List.of("code"), null);

        List<ViewableFieldPaths.FieldPath> out =
                ViewableFieldPaths.dedupByPath(List.of(name, nameAgain, code));

        assertEquals(2, out.size());
        assertSame(name, out.get(0), "first occurrence of the duplicated path is kept");
        assertEquals(List.of("code"), out.get(1).path());
    }

    @Test
    void collectSurfacesIdentityExactlyOnce() {
        // Identity (name + qid) must appear once each — never doubled — so a
        // duplicated field can't build an inconsistent composite sort/search key.
        ViewConfig config = ViewConfig.of(EntityCard.class);

        List<ViewableFieldPaths.FieldPath> paths = ViewableFieldPaths.collect(
                config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        List<List<String>> allPaths = paths.stream()
                .map(ViewableFieldPaths.FieldPath::path)
                .collect(Collectors.toList());

        assertEquals(allPaths.stream().distinct().count(), allPaths.size(),
                "no duplicate paths: " + allPaths);
        assertEquals(1, allPaths.stream().filter(p -> p.equals(List.of("name"))).count());
        assertEquals(1, allPaths.stream().filter(p -> p.equals(List.of("qid"))).count());
    }

    @Test
    void nonEntityDoesNotGetSyntheticQid() {
        // No qid field → identity-field injection is a no-op (existing behavior).
        ViewConfig config = ViewConfig.of(TestChild.class);
        config.setAllFields(false);

        Set<String> paths = pathStrings(ViewableFieldPaths.collect(
                config, ViewableFieldPaths.NOT_MEDIA_FIELDS));

        assertFalse(paths.contains("qid"), paths.toString());
    }
}