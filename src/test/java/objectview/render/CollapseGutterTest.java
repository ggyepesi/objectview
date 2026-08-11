package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.Inline;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

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

    /** A collapsed card is a single header row: there is nothing to collapse, and its
     *  left edge is ordinary content that must keep behaving as such. */
    @Test void aCollapsedCardHasNoStripArmed() throws Exception {
        RenderContext context = collapsibleContext();
        Entry root = entryWithBody();
        Card card = cardFor(context, root);

        assertFalse(context.isCardExpanded(root, false), "starts collapsed");
        for (java.awt.event.MouseListener listener : card.getMouseListeners()) {
            assertFalse(listener instanceof CollapseGutter,
                        "a collapsed card must not arm the strip");
        }
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
