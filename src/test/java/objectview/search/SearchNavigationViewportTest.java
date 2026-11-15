package objectview.search;

import objectview.EdtTests;
import objectview.ViewableAdapter;
import objectview.field.ViewableContractFieldSet;
import objectview.view.SearchableView;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Replacing a broad search must not leave a narrower result above the old viewport. */
class SearchNavigationViewportTest {

    @Test void firstResultPublishedBeforeLayoutIsVisibleAfterLayout() {
        EdtTests.onEdt(() -> {
            ManyFields laocoon = new ManyFields();
            SearchableView view = SearchableView.builder(List.of(laocoon))
                    .sample(laocoon).build();

            view.search().runCoordinatedSearch("laoc");
            JScrollPane navigation = resultScroll(view.search());
            navigation.setSize(new Dimension(320, 55));
            layoutTree(navigation);

            JViewport viewport = navigation.getViewport();
            Component row = ((Container) viewport.getView()).getComponent(0);
            assertTrue(viewport.getViewRect().intersects(row.getBounds()),
                    "the first published navigation row must be inside the viewport");
        });
    }

    @Test void narrowingAScrolledResultListRevealsItsSingleNavigationRow() {
        EdtTests.onEdt(() -> {
            ManyFields laocoon = new ManyFields();
            ViewConfig search = ViewConfig.of(ManyFields.class);
            search.setAllFields(false);
            search.addField(ViewableContractFieldSet.DISPLAY_KEY, ViewConfig.leaf());
            for (String field : List.of("one", "two", "three", "four", "five",
                    "six", "seven", "eight")) {
                search.addField(field, ViewConfig.leaf());
            }
            SearchableView view = SearchableView.builder(List.of(laocoon))
                    .sample(laocoon)
                    .configState(new SearchPanel.ConfigState(search, null, null))
                    .build();

            view.search().runCoordinatedSearch("lao");
            JScrollPane navigation = resultScroll(view.search());
            navigation.setSize(new Dimension(320, 55));
            navigation.doLayout();
            JComponent resultRows = (JComponent) navigation.getViewport().getView();
            resultRows.setSize(resultRows.getPreferredSize());
            navigation.doLayout();
            assertTrue(navigation.getVerticalScrollBar().getMaximum()
                            > navigation.getVerticalScrollBar().getVisibleAmount(),
                    "a long navigation list must expose a working vertical scroll range");
            navigation.getViewport().setViewPosition(new Point(0, 90));
            assertTrue(navigation.getViewport().getViewPosition().y > 0,
                    "the forcing case needs an already-scrolled broad result list");

            view.search().runCoordinatedSearch("laoc");

            assertEquals(1, view.search().virtualHitTotal());
            assertEquals(0, navigation.getViewport().getViewPosition().y,
                    "the remaining navigation row must be visible at the top");
        });
    }

    private static JScrollPane resultScroll(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JScrollPane scroll
                    && scroll.getVerticalScrollBarPolicy()
                    != JScrollPane.VERTICAL_SCROLLBAR_NEVER) {
                return scroll;
            }
            if (child instanceof Container nested) {
                try {
                    return resultScroll(nested);
                } catch (IllegalStateException ignored) {
                    // Continue with the next branch.
                }
            }
        }
        throw new IllegalStateException("Search result scroll pane not found");
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    public static final class ManyFields extends ViewableAdapter {
        public String one = "lao one";
        public String two = "lao two";
        public String three = "lao three";
        public String four = "lao four";
        public String five = "lao five";
        public String six = "lao six";
        public String seven = "lao seven";
        public String eight = "lao eight";

        @Override public String getIdentifier() { return "Q22878"; }
        @Override public String getDisplayName() { return "Laocoön"; }
    }
}
