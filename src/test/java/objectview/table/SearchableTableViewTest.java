package objectview.table;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.field.ViewableFieldPaths;
import objectview.media.MediaValue;
import objectview.search.SearchAndSort;
import objectview.search.SearchPanel;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchableTableViewTest {

    @Test void viewConfigDefinesColumnsAndRawRowsRemainTheData() {
        Item first = item("one", List.of("alpha", "beta"));
        Item second = item("two", List.of("gamma"));
        ViewConfig view = ViewConfig.of(Item.class);
        view.setAllFields(false);
        view.addField("name", ViewConfig.leaf());

        SearchableTableView tableView = SearchableTableView.builder(List.of(first, second))
                .sample(first)
                .configState(new SearchPanel.ConfigState(null, null, view))
                .build();

        assertEquals(2, tableView.table().items().size());
        assertEquals(1, tableView.table().getColumnCount());
        assertEquals("name", tableView.table().viewableModel().column(0).dotted());
        assertEquals("one", tableView.table().getValueAt(0, 0));
    }

    @Test void searchRevealSelectsTheMatchingCollectionElement() {
        Item item = item("one", List.of("alpha", "beta"));
        SearchableTableView tableView = SearchableTableView.builder(List.of(item))
                .sample(item)
                .build();
        ViewableTable table = tableView.table();
        int tags = modelColumn(table, "tags");
        ViewableFieldPaths.PathInfo path = table.viewableModel().column(tags);
        table.moveColumn(table.convertColumnIndexToView(tags), 0);

        table.revealSearchHit(item, path, List.of("beta"));

        int tagsView = table.convertColumnIndexToView(tags);
        String text = rendererText(table, 0, tagsView);
        assertTrue(text.contains("beta"), text);
        assertTrue(text.contains("2/2"), text);
    }

    @Test void boundDisplayFieldIsOnePhysicalColumnNotSyntheticName() {
        Item item = item("one", List.of());
        ViewableTable table = SearchableTableView.builder(List.of(item))
                .sample(item)
                .build().table();

        List<String> paths = java.util.stream.IntStream.range(0, table.getColumnCount())
                .mapToObj(index -> table.viewableModel().column(index).dotted())
                .toList();
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

        ViewableTable table = SearchableTableView.builder(List.of(complete, missing))
                .sample(complete)
                .configState(new SearchPanel.ConfigState(null, null, parentConfig))
                .build().table();

        assertEquals(2, table.getColumnCount());
        int nestedColumn = modelColumn(table, "nested.label");
        assertEquals("Budapest", table.viewableModel().getValueAt(0, nestedColumn));
        assertEquals(null, table.viewableModel().getValueAt(1, nestedColumn));
    }

    @Test void tableHeightLeavesRoomForThumbnailAndMediaSourceIsNotRenderedAsValue() {
        // A blank source keeps this a pure renderer test: no file/network load is
        // started merely to verify the cell's loading presentation.
        MediaItem item = new MediaItem("Flag", new TestMedia("source text", ""));
        ViewableTable table = SearchableTableView.builder(List.of(item))
                .sample(item)
                .build().table();
        int imageColumn = modelColumn(table, "image");

        assertTrue(table.getRowHeight(0) >= 150);
        String rendered = rendererText(
                table, 0, table.convertColumnIndexToView(imageColumn));
        assertTrue(rendered.contains("image unavailable"), rendered);
        assertTrue(!rendered.contains("source text"), rendered);
    }

    @Test void rowHeightIsAUniformTableLayoutChoiceRatherThanMediaDetection() {
        Item textOnly = item("one", List.of("alpha"));
        ViewableTable table = SearchableTableView.builder(List.of(textOnly))
                .sample(textOnly)
                .build().table();

        assertTrue(table.getRowHeight(0) >= 150);
    }

    private static int modelColumn(ViewableTable table, String path) {
        for (int i = 0; i < table.getColumnCount(); i++) {
            if (path.equals(table.viewableModel().column(i).dotted())) return i;
        }
        throw new AssertionError("Missing column " + path);
    }

    private static String rendererText(JTable table, int row, int column) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component component = renderer.getTableCellRendererComponent(
                table, table.getValueAt(row, column), false, false, row, column);
        return componentText(component);
    }

    private static String componentText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel label) text.append(label.getText()).append(' ');
        if (component instanceof Container container) {
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
}
