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

    private static final class Item extends ViewableAdapter {
        @Override public String getIdentifier() { return "item"; }
        @Override public String getDisplayName() { return "Item"; }
    }

    private static class DataTarget implements VirtualizedContainer {
        private List<Viewable> items = new ArrayList<>(List.of(new Item()));
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
        @Override public void setViewConfigResolver(
                Function<Viewable, ViewConfig> resolver) { }
    }
}
