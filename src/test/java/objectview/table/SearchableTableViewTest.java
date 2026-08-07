package objectview.table;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Link;
import objectview.annotations.Inline;
import objectview.field.FieldProperties;
import objectview.field.ViewableFieldPaths;
import objectview.media.ImagePane;
import objectview.media.MediaValue;
import objectview.render.RenderingMode;
import objectview.render.LinkRow;
import objectview.render.ReferenceRow;
import objectview.render.TextRow;
import objectview.search.SearchAndSort;
import objectview.search.SearchPanel;
import objectview.view.SearchableView;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TABLE mode is now a LAYOUT of the card path ({@link ViewableColumnsView}), so these tests
 * assert the component contract — columns come from the view config, and each cell reuses the
 * card path's value components (an image is an {@link ImagePane}, never its label text).
 */
class SearchableTableViewTest {

    @Test void viewConfigDefinesColumnsAndRawRowsRemainTheData() {
        Item first = item("one", List.of("alpha", "beta"));
        Item second = item("two", List.of("gamma"));
        ViewConfig view = ViewConfig.of(Item.class);
        view.setAllFields(false);
        view.addField("name", ViewConfig.leaf());

        ViewableColumnsView table = SearchableView.builder(List.of(first, second))
                .mode(RenderingMode.TABLE)
                .sample(first)
                .configState(new SearchPanel.ConfigState(null, null, view))
                .build().table();

        assertEquals(2, table.items().size());
        assertEquals(List.of("name"), dottedColumns(table));
        assertTrue(values(table.row(first)).contains("one"), "the row shows its data value");
    }

    @Test void searchRevealHighlightsTheRowAndTheCellShowsEveryCollectionElement() {
        Item item = item("one", List.of("alpha", "beta"));
        ViewableColumnsView table = SearchableView.builder(List.of(item))
                .mode(RenderingMode.TABLE)
                .sample(item)
                .build().table();
        ViewableFieldPaths.PathInfo tags = column(table, "tags");

        JComponent revealed = table.revealSearchHit(item, tags, List.of("beta"));

        assertNotNull(revealed, "revealing a hit navigates to (and returns) its row");
        // The whole collection renders in the cell — no per-element cursor is needed.
        String rowText = values(table.row(item));
        assertTrue(rowText.contains("alpha") && rowText.contains("beta"), rowText);
    }

    @Test void boundDisplayFieldIsOnePhysicalColumnNotSyntheticName() {
        Item item = item("one", List.of());
        ViewableColumnsView table = SearchableView.builder(List.of(item))
                .mode(RenderingMode.TABLE)
                .sample(item)
                .build().table();

        List<String> paths = dottedColumns(table);
        assertTrue(paths.contains("name"), paths.toString());
        assertTrue(!paths.contains(
                objectview.field.ViewableContractFieldSet.DISPLAY_KEY), paths.toString());
    }

    @Test void mapKeysParticipateInSharedSearch() {
        Item item = item("one", List.of());
        item.facts.put("symbol", "H");
        ViewConfig config = ViewConfig.of(Item.class);
        config.setAllFields(false);
        config.addField("facts", ViewConfig.leaf());
        List<ViewableFieldPaths.PathInfo> paths =
                ViewableFieldPaths.collectFromSample(
                        item, config, ViewableFieldPaths.ALL_FIELDS);

        Map<String, List<objectview.Viewable>> hits = new SearchAndSort()
                .searchViewables(List.of(item), List.of("symbol"), paths);

        assertEquals(List.of(item), hits.get("facts"));
    }

    @Test void nullNestedValueLeavesConfiguredLeafCellEmptyWithoutAddingAColumn() {
        Nested nested = new Nested("Budapest");
        Parent complete = new Parent("Hungary", nested);
        Parent missing = new Parent("Unknown", null);
        ViewConfig nestedConfig = ViewConfig.of(Nested.class);
        nestedConfig.setAllFields(false);
        nestedConfig.addField("label", ViewConfig.leaf());
        ViewConfig parentConfig = ViewConfig.of(Parent.class);
        parentConfig.setAllFields(false);
        parentConfig.addField("name", ViewConfig.leaf());
        parentConfig.addField("nested", nestedConfig);

        ViewableColumnsView table = SearchableView.builder(List.of(complete, missing))
                .mode(RenderingMode.TABLE)
                .sample(complete)
                .configState(new SearchPanel.ConfigState(null, null, parentConfig))
                .build().table();

        assertEquals(List.of("name", "nested.label"), dottedColumns(table));
        assertTrue(values(table.row(complete)).contains("Budapest"));
        assertTrue(!values(table.row(missing)).contains("Budapest"),
                "a null nested value leaves the configured leaf cell empty");
    }

    @Test void aMediaCellRendersAsAnImageComponentNotItsLabelText() {
        // ON_PAINT loading means constructing the ImagePane starts no file/network load, so
        // this stays a pure rendering test.
        MediaItem withImage = new MediaItem("Flag", new TestMedia("Flag.jpg", "http://x/Flag.jpg"));
        ViewableColumnsView table = SearchableView.builder(List.of(withImage))
                .mode(RenderingMode.TABLE)
                .sample(withImage)
                .build().table();

        JComponent row = table.row(withImage);
        assertNotNull(find(row, ImagePane.class),
                "a media value becomes an ImagePane — the same component cards use");
        assertTrue(!values(row).contains("Flag.jpg"),
                "the media source is never rendered as its label text");
    }

    @Test void aSourcelessMediaCellIsEmptyRatherThanShowingItsLabel() {
        MediaItem blank = new MediaItem("Flag", new TestMedia("Flag.jpg", ""));
        ViewableColumnsView table = SearchableView.builder(List.of(blank))
                .mode(RenderingMode.TABLE)
                .sample(blank)
                .build().table();

        JComponent row = table.row(blank);
        assertNull(find(row, ImagePane.class), "no source, no image component");
        assertTrue(!values(row).contains("Flag.jpg"),
                "a sourceless media cell shows nothing, not its label");
    }

    @Test void manyColumnsKeepAMinimumWidthSoTheContentScrollsHorizontally() {
        Item item = item("one", List.of("a"));
        item.facts.put("symbol", "H");
        ViewableColumnsView table = SearchableView.builder(List.of(item))
                .mode(RenderingMode.TABLE)
                .sample(item)
                .build().table();

        int columnCount = table.columns().size();
        assertTrue(columnCount >= 3, "fixture should exercise several columns: " + columnCount);
        // The list lays its content out at >= columns * min width, so the columns never shrink
        // below that — the content grows past a narrow viewport and scrolls horizontally.
        int contentWidth = table.scrollPane().getViewport().getView().getPreferredSize().width;
        assertTrue(contentWidth >= columnCount * 160,
                contentWidth + "px for " + columnCount + " columns");
    }

    @Test void linkAnnotationUsesTheSameClickableRowAsCardMode() {
        LinkItem item = new LinkItem("ObjectView", "https://example.test/objectview");
        ViewableColumnsView table = SearchableView.builder(List.of(item))
                .mode(RenderingMode.TABLE)
                .sample(item)
                .build().table();

        assertNotNull(find(table.row(item), LinkRow.class));
    }

    @Test void referenceColumnUsesCollapsedChipAndSharedIdentityDecoration() {
        Nested city = new Nested("Budapest");
        Parent country = new Parent("Hungary", city);
        ViewConfig config = ViewConfig.of(Parent.class);
        config.setAllFields(false);
        config.addField("name", ViewConfig.leaf());
        config.addField("nested", ViewConfig.leaf());

        ViewableColumnsView table = SearchableView.builder(List.of(country))
                .mode(RenderingMode.TABLE)
                .sample(country)
                .configState(new SearchPanel.ConfigState(null, null, config))
                .cardDecorator(value -> value == city ? new JLabel("Q1781") : null)
                .build().table();
        JComponent row = table.row(country);

        assertNotNull(find(row, ReferenceRow.class),
                "a reference column uses Card's collapsed-reference semantics");
        assertTrue(componentText(row).contains("Q1781"),
                "the same identity decorator is attached to the reference chip");
    }

    @Test void clickingAChildValueSelectsItsOwningRowWithoutConsumingTheClick() throws Exception {
        Item item = item("one", List.of("alpha"));
        java.util.concurrent.atomic.AtomicReference<Object> selected =
                new java.util.concurrent.atomic.AtomicReference<>();
        ViewableColumnsView table = SearchableView.builder(List.of(item))
                .mode(RenderingMode.TABLE)
                .sample(item)
                .selectionListener(selected::set)
                .build().table();
        TextRow value = find(table.row(item), TextRow.class);
        assertNotNull(value);

        SwingUtilities.invokeAndWait(() -> {
            MouseEvent press = new MouseEvent(value, MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 3, 3, 1, false,
                    MouseEvent.BUTTON1);
            for (java.awt.event.MouseListener listener : value.getMouseListeners()) {
                listener.mousePressed(press);
            }
            assertTrue(!press.isConsumed(), "row selection must not steal the value click");
        });
        assertEquals(item, selected.get());
    }

    @Test void rowHeightDoesNotTruncateALongRenderedCollection() {
        List<Nested> many = java.util.stream.IntStream.range(0, 40)
                .mapToObj(i -> new Nested("value " + i)).toList();
        InlineCollectionItem item = new InlineCollectionItem("one", many);
        ViewConfig config = ViewConfig.of(InlineCollectionItem.class);
        config.setAllFields(false);
        config.addField("children", ViewConfig.leaf());
        ViewableColumnsView table = SearchableView.builder(List.of(item))
                .mode(RenderingMode.TABLE)
                .sample(item)
                .configState(new SearchPanel.ConfigState(null, null, config))
                .build().table();

        assertTrue(table.row(item).getPreferredSize().height > 260,
                "content determines row height; the former 260px cap must not hide values");
    }

    private static ViewableFieldPaths.PathInfo column(ViewableColumnsView table, String dotted) {
        for (ViewableFieldPaths.PathInfo column : table.columns()) {
            if (dotted.equals(column.dotted())) return column;
        }
        throw new AssertionError("Missing column " + dotted);
    }

    private static List<String> dottedColumns(ViewableColumnsView table) {
        return table.columns().stream().map(ViewableFieldPaths.PathInfo::dotted).toList();
    }

    /** All field values rendered inside a component tree, read from the shared
     *  FIELD_VALUE client property the card path's value rows carry. */
    private static String values(Component root) {
        StringBuilder text = new StringBuilder();
        if (root instanceof JComponent component) {
            Object value = component.getClientProperty(FieldProperties.FIELD_VALUE_PROPERTY);
            // Data values only — a media cell carries its ImagePane as the value; that is the
            // image rendered, not the label text, so it must not count as rendered text.
            if (value != null && !(value instanceof Component)) text.append(value).append(' ');
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                text.append(values(child));
            }
        }
        return text.toString();
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String componentText(Component root) {
        StringBuilder text = new StringBuilder();
        if (root instanceof JLabel label) text.append(label.getText()).append(' ');
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                text.append(componentText(child));
            }
        }
        return text.toString();
    }

    private static Item item(String name, List<String> tags) {
        return new Item(name, tags);
    }

    private static final class Item extends ViewableAdapter {
        @DisplayField
        private final String name;
        private final List<String> tags;
        private final Map<String, String> facts = new LinkedHashMap<>();

        private Item(String name, List<String> tags) {
            this.name = name;
            this.tags = tags;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Parent extends ViewableAdapter {
        private final String name;
        private final Nested nested;
        private Parent(String name, Nested nested) {
            this.name = name;
            this.nested = nested;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Nested extends ViewableAdapter {
        private final String label;
        private Nested(String label) { this.label = label; }
        @Override public String getIdentifier() { return label; }
        @Override public String getDisplayName() { return label; }
    }

    private record TestMedia(String mediaLabel, String mediaUrl) implements MediaValue {
        @Override public boolean mediaSvg() { return false; }
    }

    private static final class MediaItem extends ViewableAdapter {
        @DisplayField private final String name;
        private final TestMedia image;
        private MediaItem(String name, TestMedia image) {
            this.name = name;
            this.image = image;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class LinkItem extends ViewableAdapter {
        @DisplayField private final String name;
        @Link private final String website;
        private LinkItem(String name, String website) {
            this.name = name;
            this.website = website;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class InlineCollectionItem extends ViewableAdapter {
        @DisplayField private final String name;
        @Inline private final List<Nested> children;
        private InlineCollectionItem(String name, List<Nested> children) {
            this.name = name;
            this.children = children;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
