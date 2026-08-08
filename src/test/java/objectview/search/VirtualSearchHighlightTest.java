package objectview.search;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.render.RenderRefreshHost;
import objectview.render.RenderingMode;
import objectview.view.SearchableView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A virtualized view highlights EVERY hit that currently has a component — not
 * just the one the search navigated to, and not just the ones that happen to be
 * (re)built afterwards.
 *
 * <p>Every assertion runs against BOTH rendering modes through the same
 * {@link RenderRefreshHost} contract: search highlighting is one code path, and
 * a mode that grows its own is a regression, not a variation.
 */
class VirtualSearchHighlightTest {

    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void highlightsEveryAlreadyBuiltHit(RenderingMode mode) {
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
    }

    @ParameterizedTest
    @EnumSource(RenderingMode.class)
    void navigatingToTheNextHitKeepsTheOtherHitsHighlighted(RenderingMode mode) {
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
    }

    @Test void clearingTheQueryClearsEveryHighlight() {
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
    private static RenderRefreshHost host(SearchableView view, Viewable item) {
        JComponent component = materialize(view, item);
        assertTrue(component instanceof RenderRefreshHost,
                "a rendered instance must be a RenderRefreshHost, was "
                        + component.getClass().getName());
        return (RenderRefreshHost) component;
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

    private static final class Element extends ViewableAdapter {
        private final String name;

        private Element(String name) {
            this.name = name;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
