package objectview.field;
import objectview.ViewableAdapter;

import objectview.annotations.Hidden;
import objectview.annotations.DisplayField;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.FieldTypeSource;
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

        List<ViewableFieldPaths.PathInfo> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("tags"), pathStrings(paths));
    }

    @Test
    void collectionOfViewableUsesNestedSelectedFieldsOnly() {
        ViewConfig childConfig = ViewConfig.of(TestChild.class);
        childConfig.setAllFields(false);
        childConfig.addField("name", ViewConfig.leaf());

        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("children", childConfig);

        List<ViewableFieldPaths.PathInfo> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("children.name"), pathStrings(paths));
    }

    @Test
    void collectFromSampleKeepsTheConfiguredReferencePath() {
        TestChild child = new TestChild();
        child.name = "Meryl";
        child.code = "42";
        TestCard card = new TestCard();
        card.name = "nom";
        card.children = List.of(child);

        // `children` is selected with no child selection: its Viewable label is the
        // searchable value, but the path remains exactly the configured reference.
        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("children", ViewConfig.leaf());

        Set<String> paths = pathStrings(ViewableFieldPaths.collectFromSample(
                card, config, ViewableFieldPaths.NOT_MEDIA_FIELDS));

        assertEquals(Set.of("children"), paths);
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
    void collapsedReferenceDoesNotInventADisplayChildPath() {
        DisplayChild child = new DisplayChild("Meryl");
        DisplayParent parent = new DisplayParent(child);
        ViewConfig config = ViewConfig.of(DisplayParent.class);
        config.setAllFields(false);
        config.addField("child", ViewConfig.leaf());

        Set<String> paths = pathStrings(ViewableFieldPaths.collectFromSample(
                parent, config, ViewableFieldPaths.NOT_MEDIA_FIELDS));

        assertEquals(Set.of("child"), paths);
    }

    @Test
    void imagePaneFieldsAreExcluded() {
        ViewConfig config = ViewConfig.of(TestCard.class);
        config.setAllFields(false);
        config.addField("name", ViewConfig.leaf());
        config.addField("image", ViewConfig.leaf());

        List<ViewableFieldPaths.PathInfo> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("name"), pathStrings(paths));
    }

    @Test
    void recursiveTypeDoesNotOverflowWhenOnlyNameIsSelected() {
        ViewConfig config = ViewConfig.of(SelfNode.class);
        config.setAllFields(false);
        config.addField("name", ViewConfig.leaf());

        List<ViewableFieldPaths.PathInfo> paths =
                ViewableFieldPaths.collect(config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        assertEquals(Set.of("name"), pathStrings(paths));
    }

    private Set<String> pathStrings(List<ViewableFieldPaths.PathInfo> paths) {
        return paths.stream()
                .map(p -> p.path().dotted())
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

    private static final class DisplayParent extends ViewableAdapter {
        private final DisplayChild child;
        private DisplayParent(DisplayChild child) { this.child = child; }
        @Override public String getIdentifier() { return "parent"; }
        @Override public String getDisplayName() { return "Parent"; }
    }

    private static final class DisplayChild extends ViewableAdapter {
        @DisplayField private final String label;
        private DisplayChild(String label) { this.label = label; }
        @Override public String getIdentifier() { return label; }
        @Override public String getDisplayName() { return label; }
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
    void allFieldsImpliesDisplayExplicitConfigDoesNot() {
        // "All fields" implies the contract display-name view. An explicit config means
        // exactly what it names — forcing name in regardless made search hit on
        // name even when the user unchecked it. Identity is never a field either way.
        ViewConfig all = ViewConfig.of(EntityCard.class);
        all.setAllFields(true);
        Set<String> allPaths = pathStrings(ViewableFieldPaths.collect(
                all, ViewableFieldPaths.NOT_MEDIA_FIELDS));
        assertTrue(allPaths.contains(ViewableContractFieldSet.DISPLAY_KEY), allPaths.toString());
        assertFalse(allPaths.contains(ViewableContractFieldSet.IDENTITY_KEY), allPaths.toString());

        ViewConfig explicit = ViewConfig.of(EntityCard.class);
        explicit.setAllFields(false);
        Set<String> explicitPaths = pathStrings(ViewableFieldPaths.collect(
                explicit, ViewableFieldPaths.NOT_MEDIA_FIELDS));
        assertFalse(explicitPaths.contains(ViewableContractFieldSet.DISPLAY_KEY),
                explicitPaths.toString());
    }

    @Test
    void dedupByPathKeepsFirstOfEachDistinctPath() {
        ViewableFieldPaths.PathInfo name =
                new ViewableFieldPaths.PathInfo(
                        "name", FieldPath.of("name"), null);
        ViewableFieldPaths.PathInfo nameAgain =
                new ViewableFieldPaths.PathInfo(
                        "name (dup)", FieldPath.of("name"), null);
        ViewableFieldPaths.PathInfo code =
                new ViewableFieldPaths.PathInfo(
                        "code", FieldPath.of("code"), null);

        List<ViewableFieldPaths.PathInfo> out =
                ViewableFieldPaths.dedupByPath(List.of(name, nameAgain, code));

        assertEquals(2, out.size());
        assertSame(name, out.get(0), "first occurrence of the duplicated path is kept");
        assertEquals(FieldPath.of("code"), out.get(1).path());
    }

    @Test
    void collectSurfacesDisplayExactlyOnceAndNeverIdentity() {
        // The display name must appear exactly once — never doubled — so a duplicated
        // field can't build an inconsistent composite sort/search key. Identity is not
        // a field, so it never appears.
        ViewConfig config = ViewConfig.of(EntityCard.class);

        List<ViewableFieldPaths.PathInfo> paths = ViewableFieldPaths.collect(
                config, ViewableFieldPaths.NOT_MEDIA_FIELDS);

        List<FieldPath> allPaths = paths.stream()
                .map(ViewableFieldPaths.PathInfo::path)
                .collect(Collectors.toList());

        assertEquals(allPaths.stream().distinct().count(), allPaths.size(),
                "no duplicate paths: " + allPaths);
        assertEquals(1, allPaths.stream().filter(p -> p.equals(
                FieldPath.of(ViewableContractFieldSet.DISPLAY_KEY))).count());
        assertEquals(0, allPaths.stream().filter(p -> p.equals(
                FieldPath.of(ViewableContractFieldSet.IDENTITY_KEY))).count());
    }

    @Test
    void explicitEmptyConfigDoesNotImplyContractFields() {
        ViewConfig config = ViewConfig.of(TestChild.class);
        config.setAllFields(false);

        Set<String> paths = pathStrings(ViewableFieldPaths.collect(
                config, ViewableFieldPaths.NOT_MEDIA_FIELDS));

        assertFalse(paths.contains(ViewableContractFieldSet.DISPLAY_KEY), paths.toString());
    }

    @Test void schemaEnumeratesNestedAndAbsentFieldsWithoutASample() {
        FieldTypeSource child = source(List.of(
                FieldRef.of("code", FieldKind.TEXT, "String", false, false, false)));
        FieldRef category = FieldRef.described("category", FieldKind.REFERENCE,
                FieldKind.REFERENCE, "Category", true, false, "Category",
                false, false, false, false, "", false);
        FieldRef image = FieldRef.of("image", FieldKind.MEDIA, "Image",
                false, false, false);
        FieldTypeSource root = source(List.of(category, image), child);

        ViewConfig nested = ViewConfig.of(TestCard.class);
        nested.setAllFields(false);
        ViewConfig categoryConfig = ViewConfig.leaf();
        categoryConfig.setAllFields(true);
        nested.addField("category", categoryConfig);
        nested.addField("image", ViewConfig.leaf());

        Set<String> paths = pathStrings(ViewableFieldPaths.collectFromSchema(
                nested, root, true));
        assertEquals(Set.of("category.code"), paths);
    }

    private static FieldTypeSource source(List<FieldRef> refs) {
        return source(refs, null);
    }

    private static FieldTypeSource source(
            List<FieldRef> refs, FieldTypeSource categoryChildren) {
        return new FieldTypeSource() {
            @Override public FieldTypeInfo field(String name) {
                FieldRef ref = refs.stream().filter(f -> f.name().equals(name))
                        .findFirst().orElse(null);
                if (ref == null) return null;
                FieldTypeSource nested = "category".equals(name)
                        ? categoryChildren : null;
                return new FieldTypeInfo(ref.typeLabel(), ref.structural(), ref.minor(),
                        ref.targetType(), nested, ref.label(), ref.role(),
                        ref.kind(), ref.valueKind());
            }

            @Override public List<String> fieldNames() {
                return refs.stream().map(FieldRef::name).toList();
            }
        };
    }
}
