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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void undisplayedZeroSizeViewportDoesNotMaterializeUntilLayout() {
        List<Item> items = makeItems(20);
        AtomicInteger builds = new AtomicInteger();
        VirtualizedCardList list = new VirtualizedCardList(q -> {
            builds.incrementAndGet();
            return card(q);
        });
        JScrollPane scroll = new JScrollPane();
        list.install(scroll);

        list.setItems(new ArrayList<>(items));
        assertEquals(0, builds.get(),
                "model setup must not masquerade as visible rendering");

        scroll.getViewport().setSize(VIEW_W, VIEW_H);
        assertTrue(builds.get() > 0,
                "real viewport layout triggers natural visible materialization");
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
    void expandingTheLastCardFromTheBottomExtendsTheScrollRange() {
        onEdt(() -> {
            List<Item> items = makeItems(200);
            VirtualizedCardList list = install(items);
            int last = items.size() - 1;

            // Scroll to the very bottom so the LAST card is built and pinned — the
            // exact situation where an expand used to leave the body unreachable.
            list.navigateToTop(items.get(last));
            int collapsedTotal = list.totalHeight();

            // Expand the last card in place and invalidate it (the real toggle path
            // used by CardListView's card-toggle handler).
            realHeight.put(items.get(last), EXPANDED);
            list.invalidateCard(items.get(last));

            // The content height must grow by EXACTLY the expansion — never left at
            // the collapsed estimate by an intermediate rebuild, which would clamp
            // the viewport up and hide the expanded body past an unreachable bottom.
            assertEquals(collapsedTotal + (EXPANDED - COLLAPSED), list.totalHeight(),
                    "content height must reflect the last card's exact expanded height");

            // The preferred (scrollable) height must cover the full content, so the
            // scrollbar range reaches the expanded bottom.
            assertTrue(list.getPreferredSize().height >= list.totalHeight(),
                    "preferred height must cover the expanded content");

            JComponent card = list.builtCard(items.get(last));
            assertNotNull(card, "last card must stay built after invalidate");
            assertEquals(EXPANDED, card.getHeight(),
                    "last card must be laid out at its expanded height");
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

    /** Realized-frame integration: unlike the other cases (which set the viewport extent
     *  by hand and read back the view size), this builds a real JScrollPane, packs it in a
     *  frame, and lets Swing's ScrollPaneLayout decide scrollbar visibility — the only way
     *  to prove the vertical scrollbar actually appears when a short (non-scrolling) list
     *  grows past the viewport on expand. Skipped when headless. */
    @Test
    void expandingAShortListShowsTheScrollbarInARealScrollPane() {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                java.awt.GraphicsEnvironment.isHeadless(), "needs a display");
        onEdt(() -> {
            List<Item> items = makeItems(2);   // 2 short cards fit a tall viewport
            VirtualizedCardList list = new VirtualizedCardList(this::card);
            JScrollPane sp = new JScrollPane();
            sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            list.install(sp);
            list.setItems(new ArrayList<>(items));

            javax.swing.JFrame frame = new javax.swing.JFrame();
            frame.setContentPane(sp);
            frame.setSize(VIEW_W + 40, 900);   // viewport taller than the 2 collapsed cards
            frame.setVisible(true);
            frame.validate();

            assertFalse(sp.getVerticalScrollBar().isShowing(),
                    "precondition: the short list must not need a scrollbar");

            // Exactly the real toggle path: invalidate + ensureVisible, and rely ONLY on
            // ensureVisible's own scroll-pane sync — no external frame.validate() nudge.
            realHeight.put(items.get(0), 2000);   // expand the first card well past the view
            list.invalidateCard(items.get(0));
            list.ensureVisible(items.get(0));

            try {
                assertTrue(sp.getVerticalScrollBar().isShowing(),
                        "the vertical scrollbar must appear once the expanded content "
                                + "exceeds the viewport");
            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    void invalidateCardAloneSyncsTheScrollRangeForBothExpandAndCollapse() {
        onEdt(() -> {
            List<Item> items = makeItems(20);
            VirtualizedCardList list = install(items);
            Item first = items.get(0);
            list.navigateToTop(first);

            // Expand via invalidateCard ONLY (no ensureVisible): the collapse path takes
            // exactly this route, and the synchronous expand must not depend on the
            // deferred ensureVisible to grow the scroll range.
            realHeight.put(first, 900);
            list.invalidateCard(first);
            assertEquals(list.getPreferredSize().height, list.getHeight(),
                    "invalidateCard alone must sync the view height on expand");

            // Collapse back: the range must shrink immediately too, not linger stale.
            realHeight.put(first, COLLAPSED);
            list.invalidateCard(first);
            assertEquals(list.getPreferredSize().height, list.getHeight(),
                    "invalidateCard alone must sync the view height on collapse");
        });
    }

    @Test
    void expandingACardInAShortListThatDidNotScrollAddsTheScrollRange() {
        onEdt(() -> {
            // A short log: a single collapsed card fits the viewport, so there is no
            // scrollbar at all. COLLAPSED (100) <= VIEW_H (400).
            List<Item> items = makeItems(1);
            VirtualizedCardList list = install(items);
            Item only = items.get(0);
            list.navigateToTop(only);

            assertTrue(list.getPreferredSize().height <= VIEW_H,
                    "precondition: the short content must fit without scrolling");

            // Expand it past the viewport — now the content needs scrolling.
            realHeight.put(only, 700);
            list.invalidateCard(only);
            list.ensureVisible(only);

            assertTrue(list.getPreferredSize().height > VIEW_H,
                    "expansion must push the content past the viewport");
            assertEquals(list.getPreferredSize().height, list.getHeight(),
                    "the view must adopt the taller height immediately so the newly "
                            + "needed scrollbar appears — even though the list did not "
                            + "scroll before");
        });
    }

    @Test
    void expandingAnAlreadyVisibleCardUpdatesTheScrollRange() {
        onEdt(() -> {
            List<Item> items = makeItems(20);
            VirtualizedCardList list = install(items);

            // The first card is already at the top and fully visible; expanding it
            // requires no scroll-to-reveal, so ensureVisible's scrollRectToVisible is
            // a no-op — but the scroll range must still grow.
            Item first = items.get(0);
            list.navigateToTop(first);

            realHeight.put(first, 700);
            list.invalidateCard(first);
            list.ensureVisible(first);

            assertEquals(list.getPreferredSize().height, list.getHeight(),
                    "expanding an already-visible card must still update the view height");
        });
    }

    @Test
    void expandingLastCardAtBottomImmediatelyUpdatesScrollableHeight() {
        onEdt(() -> {
            List<Item> items = makeItems(20);
            Item last = items.get(items.size() - 1);
            VirtualizedCardList list = install(items);

            list.navigateToTop(last);
            int collapsedHeight = list.getHeight();

            realHeight.put(last, 700);
            list.invalidateCard(last);
            list.ensureVisible(last);

            assertTrue(list.getPreferredSize().height > collapsedHeight,
                    "expansion must increase the virtual content height");
            assertEquals(list.getPreferredSize().height, list.getHeight(),
                    "the viewport view must adopt the new height immediately so "
                            + "downward scrolling is not clamped to the old bottom");
        });
    }

    @Test
    void expandingLastCardPublishesTheNewBottomToTheScrollbarImmediately() {
        onEdt(() -> {
            List<Item> items = makeItems(20);
            Item last = items.get(items.size() - 1);
            VirtualizedCardList list = new VirtualizedCardList(this::card);
            JScrollPane scroll = new JScrollPane();
            scroll.setSize(VIEW_W + 20, VIEW_H);
            list.install(scroll);
            list.setItems(new ArrayList<>(items));
            scroll.doLayout();

            list.navigateToTop(last);
            realHeight.put(last, 900);
            list.invalidateCard(last);

            javax.swing.JScrollBar bar = scroll.getVerticalScrollBar();
            int bottom = bar.getMaximum() - bar.getVisibleAmount();
            assertEquals(list.getPreferredSize().height, bar.getMaximum(),
                    "the scrollbar maximum must receive the expanded view height "
                            + "during the toggle, without a compensating scroll event");

            bar.setValue(bottom);
            assertEquals(bottom, scroll.getViewport().getViewPosition().y,
                    "the expanded last card's bottom must be reachable immediately");
            assertTrue(bottom + scroll.getViewport().getExtentSize().height
                            >= list.totalHeight(),
                    "the reachable range must include the expanded card's bottom");
        });
    }
}
