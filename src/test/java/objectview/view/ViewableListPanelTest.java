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
