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
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test void anUpdatedCardReplacesItsAlreadyIndexedText() {
        EdtTests.onEdt(() -> {
            Item item = new Item("old title");
            SearchPanel search = new SearchPanel(Item.class);
            ConfigurableDataTarget target = new ConfigurableDataTarget(item);
            search.setTargetAndApplyViewConfig(target, new JPanel(), new JScrollPane());

            search.runCoordinatedSearch("old title");
            assertEquals(List.of(item), distinct(search.currentHits()));

            item.name = "new title";
            search.cardsUpdated(List.of(new objectview.render.Card(
                    item, ViewConfig.of(Item.class), false)));
            search.runCoordinatedSearch("new title");

            assertEquals(List.of(item), distinct(search.currentHits()),
                    "a mutation event must replace the cached field text");
            search.runCoordinatedSearch("old title");
            assertTrue(search.currentHits().isEmpty(),
                    "the replaced text must not remain searchable");
        });
    }

    @Test void aLargeFirstSearchIndexesOffTheEventThreadAndShowsProgress()
            throws Exception {
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch releaseExtraction = new CountDownLatch(1);
        BlockingItem blocked = new BlockingItem(
                "needle position", extractionStarted, releaseExtraction);
        List<Viewable> items = new ArrayList<>();
        items.add(blocked);
        for (int i = 1; i < SearchPanel.BACKGROUND_INDEX_READS; i++) {
            items.add(new Item("position " + i));
        }

        SearchPanel[] panel = new SearchPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new SearchPanel(Item.class);
            panel[0].setTargetAndApplyViewConfig(
                    new ConfigurableDataTarget(items.toArray(Viewable[]::new)),
                    new JPanel(), new JScrollPane());
            panel[0].runCoordinatedSearch("needle");
            assertTrue(panel[0].indexingSearch());
        });

        assertTrue(extractionStarted.await(5, TimeUnit.SECONDS));
        // This invocation can run only if the first search returned control to Swing.
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(panel[0].indexingSearch());
            JComponent status = findNamed(panel[0], "search-indexing-status");
            assertNotNull(status);
            writeArtifact((JComponent) status.getParent(),
                    "background-search-indexing.png");
        });

        releaseExtraction.countDown();
        waitForIndexing(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(panel[0].indexingSearch());
            assertEquals(List.of(blocked), distinct(panel[0].currentHits()));
        });
    }

    @Test void mutationDuringBackgroundExtractionDiscardsTheOldGeneration()
            throws Exception {
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch releaseExtraction = new CountDownLatch(1);
        BlockingItem changed = new BlockingItem(
                "old needle", extractionStarted, releaseExtraction);
        List<Viewable> items = new ArrayList<>();
        items.add(changed);
        for (int i = 1; i < SearchPanel.BACKGROUND_INDEX_READS; i++) {
            items.add(new Item("position " + i));
        }

        SearchPanel[] panel = new SearchPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new SearchPanel(Item.class);
            panel[0].setTargetAndApplyViewConfig(
                    new ConfigurableDataTarget(items.toArray(Viewable[]::new)),
                    new JPanel(), new JScrollPane());
            panel[0].runCoordinatedSearch("old needle");
        });
        assertTrue(extractionStarted.await(5, TimeUnit.SECONDS));

        SwingUtilities.invokeAndWait(() -> {
            changed.setName("fresh needle");
            panel[0].cardsUpdated(List.of(new objectview.render.Card(
                    changed, ViewConfig.of(Item.class), false)));
            panel[0].runCoordinatedSearch("fresh needle");
        });
        releaseExtraction.countDown();
        waitForIndexing(panel[0]);

        SwingUtilities.invokeAndWait(() -> {
            assertEquals(List.of(changed), distinct(panel[0].currentHits()));
            panel[0].runCoordinatedSearch("old needle");
            assertTrue(panel[0].currentHits().isEmpty(),
                    "a cancelled generation must never publish its stale text");
        });
    }

    private static List<Viewable> distinct(List<Viewable> hits) {
        List<Viewable> out = new ArrayList<>();
        for (Viewable hit : hits) if (!out.contains(hit)) out.add(hit);
        return out;
    }

    private static class Item extends ViewableAdapter {
        private String name;
        private Item() { this("item"); }
        private Item(String name) { this.name = name; }
        void setName(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class BlockingItem extends Item {
        private final CountDownLatch started;
        private final CountDownLatch release;
        private boolean blocked;
        private BlockingItem(String name, CountDownLatch started, CountDownLatch release) {
            super(name);
            this.started = started;
            this.release = release;
        }
        @Override public String getDisplayName() {
            if (!blocked) {
                blocked = true;
                started.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
            }
            return super.getDisplayName();
        }
    }

    private static JComponent findNamed(java.awt.Component root, String name) {
        if (root instanceof JComponent component && name.equals(component.getName())) {
            return component;
        }
        if (root instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JComponent found = findNamed(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void waitForIndexing(SearchPanel panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean indexing = true;
        while (indexing && System.nanoTime() < deadline) {
            final boolean[] observed = new boolean[1];
            SwingUtilities.invokeAndWait(() -> observed[0] = panel.indexingSearch());
            indexing = observed[0];
            if (indexing) Thread.sleep(20);
        }
        assertFalse(indexing, "background search indexing did not finish");
    }

    private static void writeArtifact(JComponent component, String name) {
        try {
            component.setSize(700, 240);
            component.doLayout();
            File artifact = new File("target/ui-artifacts", name);
            assertTrue(artifact.getParentFile().mkdirs()
                    || artifact.getParentFile().isDirectory());
            BufferedImage image = new BufferedImage(
                    component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            component.printAll(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", artifact);
        } catch (java.io.IOException ex) {
            throw new AssertionError("Cannot write UI artifact", ex);
        }
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
