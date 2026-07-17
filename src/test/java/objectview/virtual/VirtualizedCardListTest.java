package objectview.virtual;

import objectview.Viewable;

import objectview.virtual.VirtualizedCardList;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless test of {@link VirtualizedCardList} navigation: cards have KNOWN
 * heights, so the true position of any card is the cumulative sum of the real
 * heights before it (the "full rendering"). We simulate navigation clicks and
 * assert the list's tracked position ({@code topOf}) equals that true position,
 * and that the navigated card lands pinned at the viewport top.
 *
 * <p>The interesting case is an EXPANDED card above a jump target: it must count
 * with its real (tall) height, and it must NOT inflate the estimate for the
 * never-built collapsed cards above the target.
 */
class VirtualizedCardListTest {

    private static final int COLLAPSED = 100;
    private static final int EXPANDED = 500;
    private static final int VIEW_W = 300;
    private static final int VIEW_H = 400;

    /** A trivial Viewable identified by index. */
    private static final class Item implements Viewable {
        private final String id;

        Item(String id) { this.id = id; }

        @Override public String getIdentifier() { return id; }
        @Override public String getDisplayName() { return id; }
        @Override public objectview.field.FieldSet fields() {
            return objectview.field.FieldSet.of(this);
        }
        @Override public String toString() { return id; }
    }

    /** The current (mutable, so we can "expand") real height of each item. */
    private final Map<Viewable, Integer> realHeight = new IdentityHashMap<>();

    private JComponent card(Viewable q) {
        // Reads its height LIVE from the map, so "expanding" an item (mutating the
        // map) changes the already-built card's preferred size — exactly what
        // Card.refresh() does in the app when a reference expands in place.
        return new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(VIEW_W, realHeight.get(q));
            }
        };
    }

    private List<Item> makeItems(int n) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Item it = new Item("i" + i);
            realHeight.put(it, COLLAPSED);
            items.add(it);
        }
        return items;
    }

    private VirtualizedCardList install(List<Item> items) {
        VirtualizedCardList list = new VirtualizedCardList(this::card);
        JScrollPane sp = new JScrollPane();
        list.install(sp);
        JViewport vp = sp.getViewport();
        vp.setSize(VIEW_W, VIEW_H);           // give the viewport an extent
        list.setItems(new ArrayList<>(items));
        return list;
    }

    private JViewport viewportOf(VirtualizedCardList list) {
        return (JViewport) list.getParent();
    }

    /** True position of item n = sum of real heights of every item before it. */
    private int truePosition(List<Item> items, int n) {
        int y = 0;
        for (int i = 0; i < n; i++) {
            y += realHeight.get(items.get(i));
        }
        return y;
    }

    private void assertLandsCorrectly(VirtualizedCardList list, List<Item> items, int n) {
        Item target = items.get(n);
        list.navigateToTop(target);

        int expected = truePosition(items, n);
        assertEquals(expected, list.topOf(target),
                "tracked offset of card " + n + " must equal the true cumulative height");

        JComponent card = list.builtCard(target);
        assertNotNull(card, "navigated card " + n + " must be built");

        int viewY = viewportOf(list).getViewPosition().y;
        String ctx = " [n=" + n + " expected=" + expected + " viewY=" + viewY
                + " cardY=" + card.getY() + " total=" + list.totalHeight() + "]";
        // With one viewport of scroll-past padding, EVERY card — including the last —
        // can be pinned exactly at the viewport top (no near-end clamp / drift).
        assertEquals(expected, viewY,
                "viewport must be scrolled to the card's true offset" + ctx);
        assertEquals(viewY, card.getY(),
                "card must be pinned at the viewport top" + ctx);
    }

    @Test
    void heightEstimateMedianIsStableAndOutlierResistant() {
        VirtualizedCardList.HeightEstimate est = new VirtualizedCardList.HeightEstimate();

        for (int i = 0; i < 10; i++) {
            est.addSample(200);            // fill the window with normal heights
        }
        assertEquals(200, est.value());

        est.addSample(2000);               // an expanded/outlier card
        assertEquals(200, est.value(),
                "an outlier > 2x the estimate must not move it");

        est.addSample(210);                // a near-normal sample is accepted
        int v = est.value();
        assertTrue(v >= 195 && v <= 215, "estimate stays near the norm: " + v);

        for (int i = 0; i < 10; i++) {
            est.addSample(120);            // the window slides to a new norm
        }
        assertEquals(120, est.value(), "after refilling with 120s the median is 120");
    }

    /** Runs {@code body} on the EDT (like the real app), so async revalidate/layout
     *  doesn't race the test thread. */
    private static void onEdt(Runnable body) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(body);
        } catch (Exception e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
    }

    @Test
    void jumpsLandOnUniformCards() {
        onEdt(() -> {
            List<Item> items = makeItems(500);
            VirtualizedCardList list = install(items);

            // "Click" a spread of targets, forward and backward.
            for (int n : new int[]{0, 5, 250, 12, 499, 300, 1, 480, 60}) {
                assertLandsCorrectly(list, items, n);
            }
        });
    }

    @Test
    void jumpBelowAnExpandedCardStillLandsExactly() {
        onEdt(() -> {
            List<Item> items = makeItems(500);
            VirtualizedCardList list = install(items);

            int m = 50;
            // Visit m so it is built, then "expand" it and re-measure.
            list.navigateToTop(items.get(m));
            realHeight.put(items.get(m), EXPANDED);
            list.navigateToTop(items.get(m));   // remeasures the now-taller card

            // Jump to n > m: m's extra height must be counted exactly, and it must
            // NOT inflate the estimate for the never-built collapsed cards above n.
            for (int n : new int[]{120, 300, 51, 200, 480}) {
                assertLandsCorrectly(list, items, n);
            }

            // Jump to n < m too (unaffected by the expansion).
            for (int n : new int[]{10, 49, 0}) {
                assertLandsCorrectly(list, items, n);
            }
        });
    }

    @Test
    void targetPinnedAtTopEvenWhenOffsetEstimateIsInexact() {
        onEdt(() -> {
            List<Item> items = makeItems(400);
            // Varying collapsed heights, so the single global estimate CANNOT match
            // every unmeasured card — tops[] is necessarily approximate. The design
            // guarantee is nonetheless that the navigated card lands exactly at the
            // viewport top, with the cards below it laid out at their real heights.
            for (int i = 0; i < items.size(); i++) {
                realHeight.put(items.get(i), 80 + (i * 37) % 81);   // 80..160
            }
            VirtualizedCardList list = install(items);

            for (int n : new int[]{0, 3, 199, 50, 399, 120, 7, 300}) {
                Item target = items.get(n);
                list.navigateToTop(target);

                JComponent card = list.builtCard(target);
                assertNotNull(card, "target " + n + " must be built");

                int viewY = viewportOf(list).getViewPosition().y;
                assertEquals(viewY, card.getY(),
                        "target " + n + " must be pinned at the viewport top even "
                                + "though the offset estimate is inexact");

                // Cards from the target downward are laid out contiguously, each with
                // its real height — exact on-screen layout, regardless of the estimate.
                int y = card.getY();
                for (int i = n; i < items.size(); i++) {
                    JComponent c = list.builtCard(items.get(i));
                    if (c == null) {
                        break;   // past the built window
                    }
                    assertEquals(y, c.getY(),
                            "card " + i + " must sit directly below the previous one");
                    assertEquals((int) realHeight.get(items.get(i)), c.getHeight(),
                            "card " + i + " must be laid out at its real height");
                    y += c.getHeight();
                    if (y > viewY + VIEW_H + 3 * 160) {
                        break;   // covered enough past the viewport
                    }
                }
            }
        });
    }

    @Test
    void jumpToFirstPanelFromTheBottomLandsAtZero() {
        onEdt(() -> {
            List<Item> items = makeItems(400);
            for (int i = 0; i < items.size(); i++) {
                realHeight.put(items.get(i), 80 + (i * 37) % 81);   // varying
            }
            VirtualizedCardList list = install(items);

            // Scroll to the far end first, THEN jump to the very first panel.
            list.navigateToTop(items.get(399));
            list.navigateToTop(items.get(0));

            int viewY = viewportOf(list).getViewPosition().y;
            assertEquals(0, viewY, "jumping to the first panel must scroll to y=0");

            JComponent card = list.builtCard(items.get(0));
            assertNotNull(card, "first panel must be built");
            assertEquals(0, card.getY(), "first panel must sit at the very top");
        });
    }

    @Test
    void multipleExpansionsStayExact() {
        onEdt(() -> {
            List<Item> items = makeItems(600);
            VirtualizedCardList list = install(items);

            for (int m : new int[]{30, 100, 250, 400}) {
                list.navigateToTop(items.get(m));
                realHeight.put(items.get(m), EXPANDED);
                list.navigateToTop(items.get(m));
            }

            // Generate a batch of clicks across the whole range.
            for (int n = 0; n < 600; n += 37) {
                assertLandsCorrectly(list, items, n);
            }
        });
    }
}
