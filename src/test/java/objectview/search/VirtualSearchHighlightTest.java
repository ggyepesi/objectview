package objectview.search;

import objectview.Viewable;
import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.render.RenderedInstanceHost;
import objectview.render.RenderingMode;
import objectview.view.SearchableView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A virtualized view highlights EVERY hit that currently has a component — not
 * just the one the search navigated to, and not just the ones that happen to be
 * (re)built afterwards.
 *
 * <p>Every assertion runs against BOTH rendering modes through the same
 * {@link RenderedInstanceHost} contract: search highlighting is one code path, and
 * a mode that grows its own is a regression, not a variation.
 */
class VirtualSearchHighlightTest {

    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void equalsPrefixMatchesOneCompleteFieldValue(RenderingMode mode) {
        EdtTests.onEdt(() -> {
            Element p39 = new Element("P39");
            Element p390 = new Element("P390");
            Element decorated = new Element("property P39");
            List<Element> items = List.of(p39, p390, decorated);
            SearchableView view = build(items, p39, mode);
            materializeAll(view, items);

            view.search().runCoordinatedSearch("=P39");

            assertTrue(host(view, p39).isHighlighted());
            assertFalse(host(view, p390).isHighlighted());
            assertFalse(host(view, decorated).isHighlighted());
        });
    }

    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void highlightsEveryAlreadyBuiltHit(RenderingMode mode) {
        EdtTests.onEdt(() -> {
            Element neptunium = new Element("neptunium");
            Element tungsten = new Element("tungsten");
            Element unbibium = new Element("unbibium");
            Element unbiennium = new Element("unbiennium");
            Element helium = new Element("helium");
            List<Element> items =
                    List.of(neptunium, tungsten, unbibium, unbiennium, helium);

            SearchableView view = build(items, neptunium, mode);

            // Every component exists BEFORE the query — the on-screen state when you
            // type into the search box. Nothing is materialized during the search, so
            // build-time re-highlighting cannot mask a missing highlight pass.
            materializeAll(view, items);

            view.search().runCoordinatedSearch("un");

            // neptunium, tungsten, unbibium and unbiennium all contain "un".
            List<String> unhighlighted = new ArrayList<>();
            for (Element hit : List.of(neptunium, tungsten, unbibium, unbiennium)) {
                if (!host(view, hit).isHighlighted()) {
                    unhighlighted.add(hit.getDisplayName());
                }
            }

            assertFalse(host(view, helium).isHighlighted(),
                    mode + ": a non-matching instance must stay untinted");
            assertTrue(host(view, neptunium).isHighlighted(),
                    mode + ": the navigated-to first hit is highlighted");
            if (!unhighlighted.isEmpty()) {
                throw new AssertionError(mode
                        + ": every hit with a built component must be highlighted, "
                        + "but these were not: " + unhighlighted);
            }
            });
    }

    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void navigatingToTheNextHitKeepsTheOtherHitsHighlighted(RenderingMode mode) {
        EdtTests.onEdt(() -> {
            Element neptunium = new Element("neptunium");
            Element tungsten = new Element("tungsten");
            Element unbibium = new Element("unbibium");
            List<Element> items = List.of(neptunium, tungsten, unbibium);

            SearchableView view = build(items, neptunium, mode);
            materializeAll(view, items);
            view.search().runCoordinatedSearch("un");

            // Stepping to the next hit scrolls it to the top; the hits left behind are
            // still hits and must stay tinted (navigation moves through the matches, it
            // does not narrow them to one).
            nextHitButton(view.search()).doClick();

            for (Element hit : items) {
                assertTrue(host(view, hit).isHighlighted(),
                        mode + ": " + hit.getDisplayName()
                                + " lost its highlight on navigate");
            }
            });
    }

    @Test void clearingTheQueryClearsEveryHighlight() {
        EdtTests.onEdt(() -> {
            for (RenderingMode mode : RenderingMode.values()) {
                Element neptunium = new Element("neptunium");
                Element tungsten = new Element("tungsten");
                List<Element> items = List.of(neptunium, tungsten);

                SearchableView view = build(items, neptunium, mode);
                materializeAll(view, items);
                view.search().runCoordinatedSearch("un");
                view.search().runCoordinatedSearch("");

                for (Element item : items) {
                    assertFalse(host(view, item).isHighlighted(),
                            mode + ": " + item.getDisplayName()
                                    + " kept its highlight after the query was cleared");
                }
            }
            });
    }

    @Test void retargetingDetachesThePreviousMaterializationListener() {
        EdtTests.onEdt(() -> {
            SearchPanel search = new SearchPanel(Element.class);
            TrackingVirtualContainer first = new TrackingVirtualContainer();
            TrackingVirtualContainer second = new TrackingVirtualContainer();

            search.setTargetAndApplyViewConfig(
                    first, new JPanel(), new JScrollPane());
            assertNotNull(first.listener);

            search.setTargetAndApplyViewConfig(
                    second, new JPanel(), new JScrollPane());

            assertTrue(first.listener == null,
                    "the old presentation must no longer call into this SearchPanel");
            assertNotNull(second.listener);
            });
    }

    /** Search configuration selects content; changing its presentation cannot
     * silently remove that content from the search index. */
    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void searchConfigurationIsIndependentOfViewConfiguration(RenderingMode mode) {
        EdtTests.onEdt(() -> {
            assertTrue(matches(mode, viewOf(DISPLAY_ONLY)),
                    mode + ": configured field content remains searchable when its "
                            + "presentation changes");

            assertTrue(matches(mode, viewOf(DISPLAY_AND_NOTE)),
                    mode + ": the same content also matches when shown as text");
            });
    }

    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void highlightsNestedFieldReachedThroughACollection(RenderingMode mode) {
        EdtTests.onEdt(() -> {
            Element item = new Element("alpha");
            item.details = List.of(new Detail("resonant"));

            objectview.viewconfig.ViewConfig detail =
                    objectview.viewconfig.ViewConfig.of(Detail.class);
            detail.setAllFields(false);
            detail.addField("note", objectview.viewconfig.ViewConfig.leaf());
            objectview.viewconfig.ViewConfig config =
                    objectview.viewconfig.ViewConfig.of(Element.class);
            config.setAllFields(false);
            config.addField("details", detail);

            SearchableView view = SearchableView.builder(List.of(item))
                    .sample(item)
                    .mode(mode)
                    .collapsible(true)
                    .configState(new SearchPanel.ConfigState(config, null, config))
                    .build();
            materialize(view, item);
            view.search().setFieldHighlight(true);
            view.search().runCoordinatedSearch("resonant");

            assertTrue(host(view, item).isHighlighted(),
                    mode + ": the matching instance is highlighted");
            assertTrue(hasHighlightedPath(
                            materialize(view, item),
                            objectview.field.FieldPath.of("details", "note"),
                            List.of("resonant")),
                    mode + ": the nested matching field itself is highlighted");
        });
    }

    @ParameterizedTest
    @EnumSource(value = RenderingMode.class, names = "CARD")
    void highlightsDisplayLabelOfNestedValue(RenderingMode mode) {
        EdtTests.onEdt(() -> {
            Element item = new Element("alpha");
            item.details = List.of(new Detail("resonant"));
            String display = objectview.field.ViewableContractFieldSet.DISPLAY_KEY;
            objectview.viewconfig.ViewConfig detail =
                    objectview.viewconfig.ViewConfig.of(Detail.class);
            detail.setAllFields(false);
            detail.addField(display, objectview.viewconfig.ViewConfig.leaf());
            objectview.viewconfig.ViewConfig config =
                    objectview.viewconfig.ViewConfig.of(Element.class);
            config.setAllFields(false);
            config.addField("details", detail);

            SearchableView view = SearchableView.builder(List.of(item))
                    .sample(item)
                    .mode(mode)
                    .collapsible(true)
                    .configState(new SearchPanel.ConfigState(config, null, config))
                    .build();
            materialize(view, item);
            view.search().setFieldHighlight(true);
            view.search().runCoordinatedSearch("resonant");

            assertTrue(host(view, item).isHighlighted());
            assertTrue(hasHighlightedPath(materialize(view, item),
                            objectview.field.FieldPath.of("details"),
                            List.of("resonant")),
                    mode + ": the rendered nested label row receives the leaf highlight");
        });
    }

    @Test void searchFindsTheSameFieldWhenItsValueIsRenderedAsANavigableLink() {
        EdtTests.onEdt(() -> {
            CustomNamed christian = new CustomNamed("Christian de Duve");
            ReferencingRecord prize = new ReferencingRecord("Medicine 1974", christian);
            objectview.viewconfig.ViewConfig person =
                    objectview.viewconfig.ViewConfig.of(CustomNamed.class);
            person.setAllFields(false);
            person.addField("title", objectview.viewconfig.ViewConfig.leaf());
            objectview.viewconfig.ViewConfig search =
                    objectview.viewconfig.ViewConfig.of(ReferencingRecord.class);
            search.setAllFields(false);
            search.addField("laureates", person);
            objectview.viewconfig.ViewConfig viewConfig =
                    objectview.viewconfig.ViewConfig.of(ReferencingRecord.class);
            viewConfig.setAllFields(false);
            viewConfig.addField("laureates", objectview.viewconfig.ViewConfig.leaf());

            objectview.render.RenderContext context = new objectview.render.RenderContext();
            context.addTopLevel(prize);
            context.addTopLevel(christian);
            SearchableView view = SearchableView.builder(List.of(prize))
                    .sample(prize)
                    .renderContext(context)
                    .configState(new SearchPanel.ConfigState(search, null, viewConfig))
                    .build();
            JComponent rendered = materialize(view, prize);
            view.search().setFieldHighlight(true);
            view.search().runCoordinatedSearch("duve");

            assertTrue(host(view, prize).isHighlighted(),
                    "search finds the containing instance before rendering highlights it");
            assertTrue(hasHighlightedPath(rendered,
                            objectview.field.FieldPath.of("laureates"), List.of("duve")),
                    "the link that paints the referenced display field owns its highlight");
            writeArtifact(rendered, "multi-instance-reference-search.png");
        });
    }

    @Test void firstTopLevelHitAlsoRevealsTheSameCardsNestedHit() {
        EdtTests.onEdt(() -> {
            Element item = new Element("resonant");
            item.details = List.of(new Detail("resonant"));
            Element another = new Element("other");
            another.details = List.of(new Detail("resonant"));
            String display = objectview.field.ViewableContractFieldSet.DISPLAY_KEY;
            objectview.viewconfig.ViewConfig detail =
                    objectview.viewconfig.ViewConfig.of(Detail.class);
            detail.setAllFields(false);
            detail.addField(display, objectview.viewconfig.ViewConfig.leaf());
            objectview.viewconfig.ViewConfig config =
                    objectview.viewconfig.ViewConfig.of(Element.class);
            config.setAllFields(false);
            config.addField(display, objectview.viewconfig.ViewConfig.leaf());
            config.addField("details", detail);

            SearchableView view = SearchableView.builder(List.of(item, another))
                    .sample(item)
                    .mode(RenderingMode.CARD)
                    .collapsible(true)
                    .configState(new SearchPanel.ConfigState(config, null, config))
                    .build();
            materialize(view, item);
            materialize(view, another);
            view.search().setFieldHighlight(true);
            view.search().runCoordinatedSearch("resonant");

            assertTrue(hasHighlightedPath(materialize(view, item),
                            objectview.field.FieldPath.of("details"),
                            List.of("resonant")),
                    "the first top-level hit reveals and highlights its nested twin");
            assertTrue(view.renderContext().isCollectionExpanded(
                            another.details, false),
                    "every matching card banks expansion for all hit paths, even off-screen");
        });
    }

    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void subtypeNestedSearchPathIsComparedWithItsSubtypeViewBranch(RenderingMode mode) {
        EdtTests.onEdt(() -> {
            SubElement item = new SubElement("alpha");
            item.subtypeDetail = new Detail("resonant");
            objectview.viewconfig.ViewConfig base = viewOf(
                    objectview.field.ViewableContractFieldSet.DISPLAY_KEY);
            objectview.viewconfig.ViewConfig detail =
                    objectview.viewconfig.ViewConfig.of(Detail.class);
            detail.setAllFields(false);
            detail.addField("note", objectview.viewconfig.ViewConfig.leaf());
            objectview.viewconfig.ViewConfig subtype =
                    objectview.viewconfig.ViewConfig.of(SubElement.class);
            subtype.setAllFields(false);
            subtype.addField("subtypeDetail", detail);
            SearchPanel.ConfigState state = new SearchPanel.ConfigState(
                    base, null, base,
                    java.util.Map.of("SubElement", subtype), java.util.Map.of(),
                    java.util.Map.of("SubElement", subtype));

            SearchableView view = SearchableView.builder(List.of(item))
                    .type(Element.class)
                    .sample(item)
                    .mode(mode)
                    .collapsible(true)
                    .configState(state)
                    .subtypeConfigs(List.of(new SearchPanel.SubtypeConfig(
                            "SubElement", "Element", item, null,
                            java.util.Set.of("subtypeDetail"),
                            value -> value instanceof SubElement)))
                    .build();
            materialize(view, item);
            view.search().setFieldHighlight(true);
            view.search().runCoordinatedSearch("resonant");

            assertTrue(host(view, item).isHighlighted(),
                    mode + ": subtype-only nested search path must remain searchable");
        });
    }

    private static boolean hasHighlightedPath(
            java.awt.Component root, objectview.field.FieldPath path,
            List<String> tokens) {
        if (root instanceof objectview.render.TextBlock block
                && block.hasMatchingRow(path, tokens) && block.isOpaque()) {
            return true;
        }
        if (root instanceof objectview.render.TextRow component
                && path.equals(component.getClientProperty(
                objectview.field.FieldProperties.FIELD_PATH_PROPERTY))
                && component.isOpaque()) {
            return true;
        }
        if (root instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                if (hasHighlightedPath(child, path, tokens)) return true;
            }
        }
        return false;
    }

    private static void writeArtifact(JComponent component, String name) {
        try {
            component.setSize(Math.max(420, component.getPreferredSize().width),
                    Math.max(180, component.getPreferredSize().height));
            component.doLayout();
            File artifact = new File("target/ui-artifacts", name);
            assertTrue(artifact.getParentFile().mkdirs()
                    || artifact.getParentFile().isDirectory());
            BufferedImage image = new BufferedImage(component.getWidth(),
                    component.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            component.printAll(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", artifact);
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot write UI artifact", e);
        }
    }

    private static final String[] DISPLAY_ONLY = {
            objectview.field.ViewableContractFieldSet.DISPLAY_KEY};
    private static final String[] DISPLAY_AND_NOTE = {
            objectview.field.ViewableContractFieldSet.DISPLAY_KEY, "note"};

    private static objectview.viewconfig.ViewConfig viewOf(String... fields) {
        objectview.viewconfig.ViewConfig config =
                objectview.viewconfig.ViewConfig.of(Element.class);
        config.setAllFields(false);
        for (String field : fields) {
            config.addField(field, objectview.viewconfig.ViewConfig.leaf());
        }
        return config;
    }

    /** Searches "resonant" — present ONLY in note — with note explicitly searchable
     *  and the given view config, and reports whether the instance was tinted. */
    private static boolean matches(
            RenderingMode mode, objectview.viewconfig.ViewConfig view) {
        Element item = new Element("alpha");
        item.note = "resonant";

        objectview.viewconfig.ViewConfig search = viewOf("note");
        SearchableView v = SearchableView.builder(List.of(item))
                .sample(item)
                .mode(mode)
                .collapsible(true)
                .configState(new SearchPanel.ConfigState(search, null, view))
                .build();
        materialize(v, item);
        v.search().runCoordinatedSearch("resonant");
        return host(v, item).isHighlighted();
    }

    private static SearchableView build(
            List<Element> items, Element sample, RenderingMode mode) {
        return SearchableView.builder(items)
                .sample(sample)
                .mode(mode)
                .collapsible(true)
                .build();
    }

    private static void materializeAll(SearchableView view, List<Element> items) {
        for (Element item : items) {
            assertNotNull(materialize(view, item),
                    "the view must be able to materialize " + item.getDisplayName());
        }
    }

    private static JComponent materialize(SearchableView view, Viewable item) {
        return view.mode() == RenderingMode.TABLE
                ? view.table().row(item)
                : view.cardList().getVirtualList().buildIfNeeded(item);
    }

    /** The rendered component for one instance, seen through the layout-neutral
     *  contract the search itself uses. */
    private static RenderedInstanceHost host(SearchableView view, Viewable item) {
        JComponent component = materialize(view, item);
        assertTrue(component instanceof RenderedInstanceHost,
                "a rendered instance must be a RenderedInstanceHost, was "
                        + component.getClass().getName());
        return (RenderedInstanceHost) component;
    }

    private static javax.swing.JButton nextHitButton(java.awt.Container panel) {
        for (java.awt.Component child : panel.getComponents()) {
            if (child instanceof javax.swing.JButton button
                    && ">".equals(button.getText())) {
                return button;
            }
            if (child instanceof java.awt.Container container) {
                javax.swing.JButton found = nextHitButton(container);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Package-private: a PRIVATE nested class's fields are not reflectable, so a
    // field-level search would silently find nothing and prove nothing.
    public static class Element extends ViewableAdapter {
        private final String name;
        public String note = "";
        public List<Detail> details = List.of();

        public Element(String name) {
            this.name = name;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    public static final class SubElement extends Element {
        public Detail subtypeDetail;
        public SubElement(String name) { super(name); }
    }

    public static final class Detail extends ViewableAdapter {
        public String note;

        public Detail(String note) { this.note = note; }
        @Override public String getIdentifier() { return note; }
        @Override public String getDisplayName() { return note; }
    }

    public static final class ReferencingRecord extends ViewableAdapter {
        private final String name;
        public List<CustomNamed> laureates;
        ReferencingRecord(String name, CustomNamed laureate) {
            this.name = name;
            this.laureates = List.of(laureate);
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    public static final class CustomNamed extends ViewableAdapter {
        @objectview.annotations.DisplayField
        public String title;
        CustomNamed(String title) { this.title = title; }
        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }

    private static final class TrackingVirtualContainer
            implements objectview.virtual.VirtualizedContainer {
        private Consumer<JComponent> listener;

        @Override public List<Viewable> items() { return List.of(); }
        @Override public Viewable topVisibleItem() { return null; }
        @Override public JComponent navigateToTop(Viewable item) { return null; }
        @Override public void setItems(List<Viewable> orderedItems) {}
        @Override public void forEachMaterialized(
                BiConsumer<Viewable, JComponent> visitor) {}
        @Override public void setMaterializationListener(
                Consumer<JComponent> listener) {
            this.listener = listener;
        }
    }
}
