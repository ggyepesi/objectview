package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.Inline;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An expanded card collapses from anywhere down its left edge.
 *
 * <p>The header triangle is otherwise the only way back, and a long entry — the
 * wbgetentities list of a 20,000-member run — puts its header far off-screen once
 * scrolled: collapsing it means scrolling up to find the control first. The strip runs
 * the card's full height, so it is beside the reader wherever they are in it.
 *
 * <p>Two things have to hold together, and the second is what makes the first safe: the
 * edge collapses, and everything past the edge still belongs to the content.
 */
class CollapseGutterTest {

    private static Entry entryWithBody() {
        Entry root = new Entry("wbgetentities FIELDS [locations]");
        root.steps.add(new Entry("batch 1"));
        root.steps.add(new Entry("batch 2"));
        return root;
    }

    private static Card cardFor(RenderContext context, Entry root) throws Exception {
        Card[] rendered = new Card[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] =
                new Card(root, ViewConfig.all(Entry.class), context, false));
        return rendered[0];
    }

    private static MouseEvent pressAt(Card card, int x) {
        return new MouseEvent(card, MouseEvent.MOUSE_PRESSED,
                              System.currentTimeMillis(), 0, x, 40, 1, false,
                              MouseEvent.BUTTON1);
    }

    private static RenderContext collapsibleContext() {
        RenderContext context = new RenderContext();
        context.setCollapsibleCards(true);
        return context;
    }

    @Test void pressingTheLeftEdgeOfAnExpandedCardCollapsesIt() throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        context.toggleCardExpanded(root);   // cards default to collapsed
        Card card = cardFor(context, root);

        // Deep in the card, far below its header — the case the header triangle
        // cannot serve because it has been scrolled out of view.
        CollapseGutter.INSTANCE.mousePressed(pressAt(card, 3));

        assertFalse(context.isCardExpanded(root, false),
                    "a press on the strip must collapse the card");
    }

    @Test void pressingPastTheEdgeLeavesTheCardAlone() throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        context.toggleCardExpanded(root);   // cards default to collapsed
        Card card = cardFor(context, root);

        CollapseGutter.INSTANCE.mousePressed(pressAt(card, CollapseGutter.WIDTH + 1));

        assertTrue(context.isCardExpanded(root, false),
                   "past the strip is content — selecting or copying a row there "
                           + "must not collapse the card under the pointer");
    }

    /**
     * The press tests above drive the handler directly, so they pass even if the card
     * never registers it — which is exactly how a strip that was armed nowhere would
     * still look tested. This asserts the wiring itself.
     */
    @Test void anExpandedCardArmsTheStripAndReservesRoomForIt() throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        context.toggleCardExpanded(root);
        Card card = cardFor(context, root);

        assertTrue(armed(card), "an expanded card must register the strip handler");
        assertTrue(card.getInsets().left > CollapseGutter.WIDTH - 1,
                   "the card must reserve the strip's width, or it paints over content");
    }

    /** A card rebuilt in place runs its build again. A second registration would
     *  toggle twice per press — collapse then instantly re-expand, which reads as the
     *  strip being broken rather than double-armed. */
    @Test void rebuildingAnExpandedCardDoesNotArmTheStripTwice() throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        context.toggleCardExpanded(root);
        Card card = cardFor(context, root);

        SwingUtilities.invokeAndWait(card::refresh);

        assertEquals(1, armedCount(card), "the strip must be registered exactly once");
    }

    private static boolean armed(Card card) {
        return armedCount(card) > 0;
    }

    private static int armedCount(Card card) {
        int n = 0;
        for (java.awt.event.MouseListener listener : card.getMouseListeners()) {
            if (listener instanceof CollapseGutter) n++;
        }
        return n;
    }

    /**
     * A nested expansion gets a strip of its own. Its body is the one that gets long —
     * an expanded wbgetentities group runs for hundreds of rows — and the chip that
     * opened it scrolls off exactly like a card header does.
     */
    @Test void anExpandedNestedBodyArmsItsOwnStrip() throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        Entry nested = root.steps.iterator().next();
        nested.steps.add(new Entry("request 1"));
        context.toggleCardExpanded(root);
        context.setExpanded(nested, true);

        Card card = cardFor(context, root);

        Card body = findCardFor(card, nested);
        assertNotNull(body, "the nested entry should render an expanded body");
        assertTrue(armed(body), "the nested body must carry its own strip");
    }

    /** The nested strip collapses the REFERENCE it belongs to. Collapsing the enclosing
     *  card instead would take the whole log entry down with it — the opposite of what
     *  a reader closing one sub-entry is asking for. */
    @Test void theNestedStripCollapsesItsOwnReferenceNotTheCardAroundIt()
            throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        Entry nested = root.steps.iterator().next();
        nested.steps.add(new Entry("request 1"));
        context.toggleCardExpanded(root);
        context.setExpanded(nested, true);
        Card card = cardFor(context, root);
        Card body = findCardFor(card, nested);

        SwingUtilities.invokeAndWait(() ->
                CollapseGutter.INSTANCE.mousePressed(pressAt(body, 3)));

        assertFalse(context.isExpanded(nested, false), "the nested entry collapses");
        assertTrue(context.isCardExpanded(root, false),
                   "the log entry around it stays open");
    }

    private static Card findCardFor(java.awt.Component root, Entry target) {
        if (root instanceof Card card && card.getViewable() == target) {
            return card;
        }
        if (root instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                Card found = findCardFor(child, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** A collapsed card is a single header row: there is nothing to collapse, and its
     *  left edge is ordinary content that must keep behaving as such. */
    @Test void aCollapsedCardHasNoStripArmed() throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        Card card = cardFor(context, root);

        assertFalse(context.isCardExpanded(root, false), "starts collapsed");
        assertFalse(armed(card), "a collapsed card must not arm the strip");
    }

    private static final class Entry extends ViewableAdapter {
        private final String name;
        @Inline
        private final Collection<Entry> steps = new ArrayList<>();

        private Entry(String name) {
            this.name = name;
        }

        @Override public String getIdentifier() {
            return name;
        }

        @Override public String getDisplayName() {
            return name;
        }
    }
}
