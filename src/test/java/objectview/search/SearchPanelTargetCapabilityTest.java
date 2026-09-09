package objectview.search;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.EdtTests;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.ConfigurableVirtualizedContainer;
import objectview.virtual.VirtualizedContainer;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPanelTargetCapabilityTest {

    @Test void plainSwingTargetIsSearchOnlyRegardlessOfItsLayout() {
        EdtTests.onEdt(() -> {
            SearchPanel search = new SearchPanel(Item.class);
            search.setTarget(new JPanel(new java.awt.GridBagLayout()), new JScrollPane());

            assertFalse(search.sortAvailable(),
                    "a Swing layout is not an ordering contract");
            assertFalse(search.viewConfigurationAvailable(),
                    "a Swing panel cannot rebuild itself from a ViewConfig");
        });
    }

    @Test void dataCapabilitiesEnableOnlyTheOperationsTheyImplement() {
        EdtTests.onEdt(() -> {
            SearchPanel search = new SearchPanel(Item.class);
            search.setTargetAndApplyViewConfig(
                    new DataTarget(), new JPanel(), new JScrollPane());
            assertTrue(search.sortAvailable());
            assertFalse(search.viewConfigurationAvailable());

            search.setTargetAndApplyViewConfig(
                    new ConfigurableDataTarget(), new JPanel(), new JScrollPane());
            assertTrue(search.sortAvailable());
            assertTrue(search.viewConfigurationAvailable());
        });
    }

    @Test void attachingAViewIndexesNothingUntilSomethingIsSearched() {
        // Indexing a loaded domain costs seconds on the EDT and hundreds of
        // megabytes. Paying that when a view is merely opened froze the window
        // itself: dragging the frame lagged and a click on a chip was never
        // delivered, for a reader who had not typed anything.
        EdtTests.onEdt(() -> {
            SearchPanel search = new SearchPanel(Item.class);
            ConfigurableDataTarget target = new ConfigurableDataTarget(new Item("item"));

            search.setTargetAndApplyViewConfig(target, new JPanel(), new JScrollPane());
            assertEquals(0, search.viewableSearchIndexRevision(),
                    "opening a domain reads no value it was not asked to read");

            search.runCoordinatedSearch("item");
            assertEquals(1, search.viewableSearchIndexRevision(),
                    "the first search builds it once");

            search.runCoordinatedSearch("ite");
            assertEquals(1, search.viewableSearchIndexRevision(),
                    "and every later search reuses it");
        });
    }

    @Test void reorderingTheListRereadsNoValueAndStillListsHitsAsShown() {
        EdtTests.onEdt(() -> {
            Item first = new Item("position one");
            Item second = new Item("position two");
            SearchPanel search = new SearchPanel(Item.class);
            ConfigurableDataTarget target =
                    new ConfigurableDataTarget(first, second);
            search.setTargetAndApplyViewConfig(target, new JPanel(), new JScrollPane());

            search.runCoordinatedSearch("position");
            long indexed = search.viewableSearchIndexRevision();
            assertEquals(List.of(first, second), distinct(search.currentHits()));

            // What a sort does: the same items, a different order, no changed text.
            target.setItems(List.of(second, first));
            search.runCoordinatedSearch("position");

            assertEquals(List.of(second, first), distinct(search.currentHits()),
                    "hits are listed in the order the reader sees them");
            assertEquals(indexed, search.viewableSearchIndexRevision(),
                    "reordering re-extracts no value; only the item set and the "
                            + "searched paths decide the index");
        });
    }

    private static List<Viewable> distinct(List<Viewable> hits) {
        List<Viewable> out = new ArrayList<>();
        for (Viewable hit : hits) if (!out.contains(hit)) out.add(hit);
        return out;
    }

    private static final class Item extends ViewableAdapter {
        private final String name;
        private Item() { this("item"); }
        private Item(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static class DataTarget implements VirtualizedContainer {
        private List<Viewable> items;
        private DataTarget() { this(new Item()); }
        private DataTarget(Viewable... items) {
            this.items = new ArrayList<>(List.of(items));
        }
        @Override public List<Viewable> items() { return List.copyOf(items); }
        @Override public Viewable topVisibleItem() {
            return items.isEmpty() ? null : items.get(0);
        }
        @Override public JComponent navigateToTop(Viewable item) { return null; }
        @Override public void setItems(List<Viewable> orderedItems) {
            items = new ArrayList<>(orderedItems);
        }
    }

    private static final class ConfigurableDataTarget extends DataTarget
            implements ConfigurableVirtualizedContainer {
        private ConfigurableDataTarget() { }
        private ConfigurableDataTarget(Viewable... items) { super(items); }
        @Override public void setViewConfigResolver(
                Function<Viewable, ViewConfig> resolver) { }
    }
}
