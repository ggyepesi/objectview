package objectview.view;

import objectview.ViewableAdapter;
import objectview.annotations.Hidden;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ViewableListPanelTest {

    @Test void replacingMembersClearsASelectionOwnedByThePreviousResult() {
        Item first = new Item("first");
        AtomicReference<objectview.Viewable> reported = new AtomicReference<>(first);
        onEdt(() -> {
            ViewableListPanel panel = new ViewableListPanel(Item.class, "None");
            panel.onSelectionChanged(reported::set);

            panel.setViewables(List.of(first));
            panel.renderContextForTest().select(first, false);
            assertSame(first, panel.selected());

            panel.setViewables(List.of(new Item("second")));
            assertNull(panel.selected());
            assertNull(reported.get());
            panel.close();
        });
    }

    @Test void aHostCanGiveDoubleClickAnActivationMeaningInsteadOfInspection() {
        Item item = new Item("category");
        AtomicReference<objectview.Viewable> activated = new AtomicReference<>();
        onEdt(() -> {
            ViewableListPanel panel = new ViewableListPanel(Item.class, "None");
            panel.onActivated(activated::set);
            panel.setViewables(List.of(item));

            org.junit.jupiter.api.Assertions.assertTrue(
                    panel.renderContextForTest().activate(item));
            assertSame(item, activated.get());
            panel.close();
        });
    }

    @Test void collapsedControlsStayCollapsedWhenABrowserReplacesItsMembers() {
        onEdt(() -> {
            ViewableListPanel panel = new ViewableListPanel(Item.class, "None");
            panel.setViewables(List.of(new Item("first")));
            panel.setControlsExpanded(false);

            panel.setViewables(List.of(new Item("second")));

            assertFalse(panel.controlsExpanded());
            panel.close();
        });
    }

    @Test void sharedContextsActivateOnlyTheOwningViewAndDetachOnDispose() {
        onEdt(() -> {
            Item first = new Item("first");
            Item second = new Item("second");
            objectview.render.RenderContext context =
                    new objectview.render.RenderContext();
            AtomicInteger firstCalls = new AtomicInteger();
            AtomicInteger secondCalls = new AtomicInteger();
            SearchableView firstView = SearchableView.builder(List.of(first))
                    .renderContext(context)
                    .activationListener(ignored -> firstCalls.incrementAndGet())
                    .build();
            SearchableView secondView = SearchableView.builder(List.of(second))
                    .renderContext(context)
                    .activationListener(ignored -> secondCalls.incrementAndGet())
                    .build();

            org.junit.jupiter.api.Assertions.assertTrue(context.activate(first));
            org.junit.jupiter.api.Assertions.assertEquals(1, firstCalls.get());
            org.junit.jupiter.api.Assertions.assertEquals(0, secondCalls.get());
            firstView.dispose();
            org.junit.jupiter.api.Assertions.assertFalse(context.activate(first));
            org.junit.jupiter.api.Assertions.assertTrue(context.activate(second));
            org.junit.jupiter.api.Assertions.assertEquals(1, secondCalls.get());
            secondView.dispose();
        });
    }

    @Test void expandedControlsCannotConsumeTheCardViewportInAShortPanel() {
        onEdt(() -> {
            SearchableView view = SearchableView.builder(List.of(new Item("one")))
                    .controlsExpanded(true)
                    .build();
            view.setSize(520, 220);
            layoutTree(view);

            org.junit.jupiter.api.Assertions.assertTrue(
                    view.cardList().getCardsScrollPane().getHeight() > 0,
                    "the virtual cards must retain a visible scroll viewport");
            view.dispose();
        });
    }

    @Test void collapsedInlineControlsCanBeExpandedAfterLayout() {
        onEdt(() -> {
            SearchableView view = SearchableView.builder(List.of(new Item("one")))
                    .controlsExpanded(false)
                    .build();
            view.setSize(700, 500);
            layoutTree(view);

            view.setControlsExpanded(true);
            view.validate();
            layoutTree(view);

            org.junit.jupiter.api.Assertions.assertTrue(view.controlsExpanded());
            org.junit.jupiter.api.Assertions.assertTrue(view.search().isShowing()
                            || view.search().getHeight() > 0,
                    "expansion must give the Search/Sort/View body layout space");
            view.dispose();
        });
    }

    @Test void replaceablePropertyStyleListKeepsExpandableInlineControls() {
        onEdt(() -> {
            ViewableListPanel panel = new ViewableListPanel(Item.class, "None");
            panel.setViewables(java.util.stream.IntStream.range(0, 200)
                    .mapToObj(i -> new Item("item " + i)).toList());
            panel.setSize(900, 700);
            layoutTree(panel);
            objectview.search.SearchPanel initialSearch = descendant(
                    panel, objectview.search.SearchPanel.class);
            org.junit.jupiter.api.Assertions.assertNotNull(initialSearch);
            org.junit.jupiter.api.Assertions.assertTrue(initialSearch.isVisible(),
                    "ordinary inline search starts visible");
            panel.setControlsExpanded(false);
            org.junit.jupiter.api.Assertions.assertTrue(initialSearch.isVisible(),
                    "collapsing removes the body without poisoning its visibility");
            layoutTree(panel);
            panel.setControlsExpanded(true);
            panel.validate();
            layoutTree(panel);

            objectview.search.SearchPanel search = descendant(
                    panel, objectview.search.SearchPanel.class);
            org.junit.jupiter.api.Assertions.assertNotNull(search);
            org.junit.jupiter.api.Assertions.assertTrue(search.isVisible());
            org.junit.jupiter.api.Assertions.assertTrue(search.getHeight() >= 160,
                    "the loaded list must show the toolbar and hit navigator, not only the disclosure row");
            panel.close();
        });
    }

    private static <T extends java.awt.Component> T descendant(
            java.awt.Container root, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof java.awt.Container nested) {
                T found = descendant(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void externalControlsKeepAStableHostAcrossResultReplacement() {
        onEdt(() -> {
            ViewableListPanel panel = new ViewableListPanel(Item.class, "None");
            javax.swing.JComponent host = panel.externalControls();
            panel.setViewables(List.of(new Item("first")));
            assertSame(host, panel.externalControls());
            org.junit.jupiter.api.Assertions.assertEquals(1, host.getComponentCount());
            java.awt.Component firstControls = host.getComponent(0);

            panel.setViewables(List.of(new Item("second")));

            assertSame(host, panel.externalControls());
            org.junit.jupiter.api.Assertions.assertEquals(1, host.getComponentCount());
            org.junit.jupiter.api.Assertions.assertNotSame(
                    firstControls, host.getComponent(0));
            panel.close();
        });
    }

    @Test void combinedControlsAreCollapsedIndependentlyOfTheirResultPanels() {
        onEdt(() -> {
            ViewableListPanel first = new ViewableListPanel(Item.class, "None");
            first.setViewables(List.of(new Item("first")));
            ViewableListPanel second = new ViewableListPanel(Item.class, "None");
            second.setViewables(List.of(new Item("second")));
            SearchControlsTabs controls =
                    new SearchControlsTabs("Search / sort / view", false);
            controls.addTab("First", first);
            controls.addTab("Second", second);

            assertFalse(controls.isExpanded());
            org.junit.jupiter.api.Assertions.assertTrue(
                    labels(controls).stream().anyMatch(text -> text.contains("(2)")),
                    "the collapsed header must refresh as tabs are registered");
            controls.setExpanded(true);
            org.junit.jupiter.api.Assertions.assertTrue(controls.isExpanded());
            first.close();
            second.close();
        });
    }

    @Test void coordinatedSectionHasNoSecondCollapsibleControlsSurface() {
        onEdt(() -> {
            SearchableView view = SearchableView.builder(List.of(new Item("one")))
                    .coordinated(true)
                    .build();

            org.junit.jupiter.api.Assertions.assertFalse(
                    descendants(view, objectview.render.ExpandablePanel.class),
                    "the shared MultiSearchBar is the only controls surface");
            org.junit.jupiter.api.Assertions.assertFalse(descendants(
                    view, objectview.search.SearchPanel.class),
                    "the section must contain cards and their scrollbar only");
            assertFalse(view.search().isVisible(),
                    "an idle per-section hit navigator must consume no space");

            view.search().runCoordinatedSearch("one");
            org.junit.jupiter.api.Assertions.assertTrue(view.search().isVisible());
            view.search().runCoordinatedSearch("");
            assertFalse(view.search().isVisible());
            view.dispose();
        });
    }

    private static boolean descendants(
            java.awt.Container root, Class<? extends java.awt.Component> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) return true;
            if (child instanceof java.awt.Container nested && descendants(nested, type)) {
                return true;
            }
        }
        return false;
    }

    private static java.util.List<String> labels(java.awt.Container root) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label) out.add(label.getText());
            if (child instanceof java.awt.Container nested) out.addAll(labels(nested));
        }
        return out;
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) layoutTree(nested);
        }
    }

    private static void onEdt(Runnable work) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(work);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class Item extends ViewableAdapter {
        @Hidden private final String id;
        private Item(String id) { this.id = id; }
        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return id; }
    }
}
