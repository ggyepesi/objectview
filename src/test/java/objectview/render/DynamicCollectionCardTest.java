package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.Inline;
import objectview.field.DynamicFields;
import objectview.field.FieldKind;
import objectview.field.FieldPath;
import objectview.field.FieldProperties;
import objectview.field.FieldRef;
import objectview.field.FieldSchema;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.VirtualizedCardList;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.imageio.ImageIO;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.border.TitledBorder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicCollectionCardTest {

    @Test
    void aCompletedLargeInlineCollectionMaterializesOnlyVisibleRows() throws Exception {
        LiveParent parent = new LiveParent();
        LiveChild first = new LiveChild("request 0", "done");
        parent.steps.add(first);
        for (int i = 1; i < 14_000; i++) {
            parent.steps.add(new LiveChild("request " + i, "done"));
        }

        Card[] card = new Card[1];
        RenderContext context = new RenderContext();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            card[0] = new Card(
                    parent, ViewConfig.all(LiveParent.class), context, false);
            card[0].setSize(900, 700);
            layoutTree(card[0]);
        });

        VirtualizedCardList virtual = find(card[0], VirtualizedCardList.class);
        assertNotNull(virtual, "a large inline collection must use the shared virtual list");
        assertEquals(14_000, virtual.items().size());
        assertTrue(count(card[0], ReferenceRow.class) < 100,
                "the UI must not contain one Swing row per completed request");
        assertEquals("steps (14000)", titledBorder(card[0], "steps").getTitle());

        JComponent rowBefore = virtual.builtCard(first);
        assertNotNull(rowBefore);
        context.setExpanded(first, true);
        javax.swing.SwingUtilities.invokeAndWait(() ->
                RenderRefreshHost.refreshAncestor(find(rowBefore, ReferenceRow.class)));
        assertNotSame(rowBefore, virtual.builtCard(first),
                "opening one request must rematerialize only its virtual row");
        assertSame(virtual, find(card[0], VirtualizedCardList.class),
                "opening a request must preserve the containing list and its scroll state");
        assertNotNull(find(virtual.builtCard(first), TextBlock.class));
        render(card[0], "target/ui-artifacts/large-inline-collection.png");
    }

    @Test
    void aLargeReferenceCollectionFieldVirtualizesLikeAnInlineOne() throws Exception {
        // Two paths render a collection of Viewables: @Inline goes through
        // inlineViewable, a plain @Reference collection through
        // createReferenceFieldComponent. Virtualizing only the first left the second
        // building one ReferenceRow per member — measured on a real position
        // hierarchy as 124,087 live rows behind 114 cards, after which every layout,
        // measure and rebuild walked all of them and the event thread stopped
        // returning. The ceiling belongs to both, because it is the same problem.
        Superclassed subject = new Superclassed("Mayor of Aast");
        for (int i = 0; i < 14_000; i++) {
            subject.superClasses.add(new Superclassed("kind of position " + i));
        }

        // A large collection renders collapsed, so the rows only appear when the
        // reader opens it — which is exactly when the event thread stopped coming
        // back. Expand it, or the test watches a header and proves nothing.
        RenderContext context = new RenderContext();
        context.setCollectionExpanded(subject.superClasses, true);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            card[0] = new Card(subject, ViewConfig.all(Superclassed.class),
                    context, false);
            card[0].setSize(900, 700);
            layoutTree(card[0]);
        });

        VirtualizedCardList virtual = find(card[0], VirtualizedCardList.class);
        assertNotNull(virtual,
                "a reference collection must use the shared virtual list too");
        assertEquals(14_000, virtual.items().size());
        assertTrue(count(card[0], ReferenceRow.class) < 100,
                "one Swing row per member is what a card cannot afford: "
                        + count(card[0], ReferenceRow.class));
    }

    @Test
    void aGrowingInlineCollectionSwitchesToVirtualRenderingAtTheBoundary()
            throws Exception {
        LiveParent parent = new LiveParent();
        for (int i = 0; i < 200; i++) {
            parent.steps.add(new LiveChild("request " + i, "done"));
        }

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(
                        parent, ViewConfig.all(LiveParent.class), new RenderContext(), false));
        assertEquals(200, count(card[0], ReferenceRow.class));

        LiveChild next = new LiveChild("request 200", "done");
        parent.steps.add(next);
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0].updateInlineCollections(List.of(next)));

        VirtualizedCardList virtual = find(card[0], VirtualizedCardList.class);
        assertNotNull(virtual);
        assertEquals(201, virtual.items().size());
        assertEquals("steps (201)", titledBorder(card[0], "steps").getTitle());
    }

    @Test
    void anInlineCollectionHeaderCountsWhatTheCollectionHasNotWhatItRendered()
            throws Exception {
        // refreshInlineCollectionCounts exists for exactly this: the collection grew
        // and the card was not rebuilt. Reading the count off the rendered rows made
        // it answer "how many rows are on screen", which is the one thing this call
        // is unable to have changed — so the header could never move.
        LiveParent parent = new LiveParent();
        parent.steps.add(new LiveChild("request 0", "done"));

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(
                        parent, ViewConfig.all(LiveParent.class),
                        new RenderContext(), false));
        assertEquals("steps (1)", titledBorder(card[0], "steps").getTitle());

        parent.steps.add(new LiveChild("request 1", "done"));
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0].refreshInlineCollectionCounts());

        assertEquals("steps (2)", titledBorder(card[0], "steps").getTitle(),
                "the header says how many members the collection has");
    }

    @Test
    void aLiveInlineCollectionChangesOnlyTheEntryThatChanged() throws Exception {
        LiveParent parent = new LiveParent();
        LiveChild first = new LiveChild("first", "a very long request already opened");
        parent.steps.add(first);
        RenderContext context = new RenderContext();
        context.setExpanded(first, true);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(parent, ViewConfig.all(LiveParent.class), context, false));
        List<ReferenceRow> before = findAll(card[0], ReferenceRow.class);
        assertEquals(1, before.size());
        TextBlock openedRequest = find(card[0], TextBlock.class);
        assertNotNull(openedRequest);

        LiveChild second = new LiveChild("second", "new request");
        parent.steps.add(second);
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0].updateInlineCollections(List.of(second)));

        List<ReferenceRow> afterAppend = findAll(card[0], ReferenceRow.class);
        assertEquals(2, afterAppend.size());
        assertSame(before.get(0), afterAppend.get(0),
                "appending a request must not rebuild an opened sibling");
        assertSame(openedRequest, find(card[0], TextBlock.class),
                "the already-rendered request text remains the same component");

        ReferenceRow secondBeforeUpdate = afterAppend.get(1);
        second.request = "finished request";
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0].updateInlineCollections(List.of(second)));

        List<ReferenceRow> afterUpdate = findAll(card[0], ReferenceRow.class);
        assertSame(before.get(0), afterUpdate.get(0));
        assertNotSame(secondBeforeUpdate, afterUpdate.get(1),
                "only the log entry whose presentation changed is replaced");

        render(card[0], "target/ui-artifacts/live-inline-log.png");
    }

    @Test
    void dynamicCollectionsUseTheSameCollapsibleHeaderAsDeclaredCollections()
            throws Exception {
        DynamicThing thing = new DynamicThing();
        thing.values.put("images", List.of("one", "two"));

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(thing, ViewConfig.all(DynamicThing.class), false));

        assertNotNull(find(card[0], CollectionHeader.class),
                "snapshot-backed collections must retain an expand/collapse chip");
    }

    @Test
    void dynamicReferencesStartCollapsedLikeDeclaredReferences()
            throws Exception {
        DynamicThing parent = new DynamicThing("parent");
        DynamicThing child = new DynamicThing("child");
        child.values.put("detail", "hidden until expanded");
        parent.values.put("child", child);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(
                        parent, ViewConfig.all(DynamicThing.class), false));

        assertNotNull(find(card[0], ReferenceRow.class));
        assertEquals(1, count(card[0], Card.class),
                "a dynamic reference must not render its nested card eagerly");
    }

    @Test
    void referenceUsesItsFieldConfigWhenLogicalTypesShareOneAdapterClass()
            throws Exception {
        DynamicThing language = new DynamicThing("French");
        language.values.put("nativeName", "français");
        language.schema = () -> List.of(FieldRef.described(
                "nativeName", FieldKind.TEXT, FieldKind.TEXT, "String",
                false, false, null, false, false,
                false, false, "", false));

        DynamicThing state = new DynamicThing("France");
        List<DynamicThing> languages = List.of(language);
        state.values.put("languages", languages);
        state.schema = () -> List.of(FieldRef.described(
                "languages", FieldKind.COLLECTION, FieldKind.REFERENCE,
                "List<Language>", true, true, "Language", false, false,
                false, false, "", true));

        ViewConfig languageConfig = ViewConfig.leaf();
        languageConfig.addField("nativeName", ViewConfig.leaf());
        ViewConfig stateConfig = ViewConfig.leaf();
        stateConfig.addField("languages", languageConfig);

        RenderContext context = new RenderContext();
        // This is the important snapshot condition: State and Language have
        // different logical schemas but the same runtime adapter class.
        context.putClassConfig(DynamicThing.class, stateConfig);
        context.setCollectionExpanded(languages, true);
        context.setExpanded(language, true);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(state, stateConfig, context, false));

        assertNotNull(find(card[0], TextBlock.class),
                "the language field config must not be replaced by the "
                        + "state config merely because both use one adapter class");
    }

    @Test
    void referenceRowsUseTheTargetsQualifiedReferenceLabel() {
        DynamicThing target = new DynamicThing("Vienna") {
            @Override public String getReferenceLabel() {
                return "All/Capitals/VI/Vienna";
            }
        };

        ReferenceRow row = new ReferenceRow(
                "groups", FieldPath.of("groups"), target,
                new RenderContext(), ViewConfig.all(DynamicThing.class),
                "Vienna", false);

        assertEquals("All/Capitals/VI/Vienna",
                row.getClientProperty(
                        FieldProperties.FIELD_VALUE_PROPERTY));
    }

    @Test
    void aReferenceChipCarriesTheCardIdentityDecorationWhenNonNull() throws Exception {
        DynamicThing parent = new DynamicThing("parent");
        DynamicThing decorated = new DynamicThing("decorated");
        DynamicThing plain = new DynamicThing("plain");
        parent.values.put("a", decorated);
        parent.values.put("b", plain);

        RenderContext context = new RenderContext();
        JLabel idChip = new JLabel("Q208045");
        // The same mechanism cards use — here non-null only for one target, so the other
        // reference stays undecorated (scoped to non-null).
        context.setCardDecorator(t -> t == decorated ? idChip : null);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() ->
                card[0] = new Card(parent, ViewConfig.all(DynamicThing.class), context, false));

        assertTrue(contains(card[0], idChip),
                "a reference whose decoration is non-null carries the identity chip");
    }

    @Test
    void aLongCardTitleYieldsSpaceToItsIdentityDecoration() throws Exception {
        DynamicThing value = new DynamicThing(
                "Joseph II, Holy Roman Emperor — Apostolic King of Hungary (1780–1790)");
        JLabel idChip = new JLabel("Q76555");
        RenderContext context = new RenderContext();
        context.setCardDecorator(ignored -> idChip);

        Card[] card = new Card[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            card[0] = new Card(value, ViewConfig.all(DynamicThing.class), context, false);
            card[0].setSize(260, 120);
            layoutTree(card[0]);
        });

        JLabel title = findTitleLabel(card[0], idChip);
        assertNotNull(title);
        assertTrue(title.getX() + title.getWidth() <= idChip.getX(),
                "the title's allocated bounds must end before the QID chip begins");
    }

    private static JLabel findTitleLabel(Component root, JLabel decoration) {
        if (root instanceof JLabel label && root != decoration
                && label.getFont().isBold()) return label;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLabel found = findTitleLabel(child, decoration);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static TitledBorder titledBorder(Component root, String titlePrefix) {
        if (root instanceof JComponent component
                && component.getBorder() instanceof TitledBorder border
                && border.getTitle().startsWith(titlePrefix)) {
            return border;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                TitledBorder found = titledBorderOrNull(child, titlePrefix);
                if (found != null) return found;
            }
        }
        throw new AssertionError("No titled border starting with " + titlePrefix);
    }

    private static TitledBorder titledBorderOrNull(Component root, String titlePrefix) {
        if (root instanceof JComponent component
                && component.getBorder() instanceof TitledBorder border
                && border.getTitle().startsWith(titlePrefix)) {
            return border;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                TitledBorder found = titledBorderOrNull(child, titlePrefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void render(JComponent component, String path) throws Exception {
        File artifact = new File(path);
        assertTrue(artifact.getParentFile().mkdirs()
                || artifact.getParentFile().isDirectory());
        BufferedImage image = new BufferedImage(
                900, 320, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            component.setSize(image.getWidth(), image.getHeight());
            layoutTree(component);
            component.paint(graphics);
        });
        graphics.dispose();
        ImageIO.write(image, "png", artifact);
        assertTrue(artifact.isFile());
    }

    private static boolean contains(Component root, Component target) {
        if (root == target) return true;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (contains(child, target)) return true;
            }
        }
        return false;
    }

    private static <T extends Component> T find(
            Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int count(Component root, Class<?> type) {
        int result = type.isInstance(root) ? 1 : 0;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                result += count(child, type);
            }
        }
        return result;
    }

    private static <T extends Component> List<T> findAll(
            Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(root, type, result);
        return result;
    }

    private static <T extends Component> void collect(
            Component root, Class<T> type, List<T> result) {
        if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, result);
            }
        }
    }

    /** A plain @Reference collection — the path that is not @Inline. */
    private static final class Superclassed extends ViewableAdapter {
        private final String name;
        @objectview.annotations.Reference
        private final Collection<Superclassed> superClasses = new ArrayList<>();

        private Superclassed(String name) { this.name = name; }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class LiveParent extends ViewableAdapter {
        @Inline
        private final Collection<LiveChild> steps = new ArrayList<>();

        @Override public String getIdentifier() { return "parent"; }
        @Override public String getDisplayName() { return "live log"; }
    }

    private static final class LiveChild extends ViewableAdapter {
        private final String name;
        private String request;

        private LiveChild(String name, String request) {
            this.name = name;
            this.request = request;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static class DynamicThing
            extends ViewableAdapter implements DynamicFields {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final String id;
        private FieldSchema schema;

        private DynamicThing() {
            this("dynamic");
        }

        private DynamicThing(String id) {
            this.id = id;
        }

        @Override public Map<String, Object> dynamicFieldValues() {
            return values;
        }

        @Override public FieldSchema dynamicFieldSchema() {
            return schema;
        }

        @Override public String getIdentifier() {
            return id;
        }

        @Override public String getDisplayName() {
            return id;
        }
    }
}
