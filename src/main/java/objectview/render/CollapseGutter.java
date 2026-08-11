package objectview.render;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * The left edge of an expanded card, clickable along its whole height to collapse it.
 *
 * <p>The header triangle is the only other way back, and once a long card is scrolled
 * the header is off-screen: collapsing means scrolling up to find it first. A card's
 * left edge, by contrast, is beside you wherever you are in it — so the affordance is
 * where the reader already is.
 *
 * <p>One shared listener resolves the card from the event source, rather than two
 * listeners per card. Only one card can be hovered at a time, so the hover state is a
 * single field here instead of per-card state — the same reason the card renderer
 * dispatches text rows from one handler by position.
 */
final class CollapseGutter implements MouseListener, MouseMotionListener {

    /** Width of the strip, and so the card's extra left inset while expanded. */
    static final int WIDTH = 12;

    private static final Color LINE = new Color(0, 80, 180, 70);
    private static final Color LINE_HOVER = new Color(0, 80, 180);
    private static final Color FILL_HOVER = new Color(0, 80, 180, 26);

    static final CollapseGutter INSTANCE = new CollapseGutter();

    /** The card whose gutter the pointer is currently over, or null. */
    private JComponent hovered;

    private CollapseGutter() { }

    /** Arms this card's gutter. Called only while the card is expanded — a collapsed
     *  card has no gutter, so its whole width keeps its normal behaviour. */
    static void install(Card card) {
        card.addMouseListener(INSTANCE);
        card.addMouseMotionListener(INSTANCE);
    }

    /** Paints the strip, from the card's own paintComponent so it sits under the
     *  card's content and never intercepts a child's own painting. */
    void paint(Graphics g, JComponent card, int height) {
        boolean hot = card == hovered;
        if (hot) {
            g.setColor(FILL_HOVER);
            g.fillRect(0, 0, WIDTH, height);
        }
        g.setColor(hot ? LINE_HOVER : LINE);
        g.fillRect(WIDTH / 2 - 1, 2, 2, Math.max(0, height - 4));
    }

    private static boolean inGutter(MouseEvent e) {
        return e.getX() >= 0 && e.getX() < WIDTH;
    }

    private void hover(JComponent card, boolean on) {
        JComponent next = on ? card : null;
        if (hovered == next) {
            return;
        }
        JComponent previous = hovered;
        hovered = next;
        if (previous != null) previous.repaint();
        if (next != null) next.repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!(e.getSource() instanceof Card card) || !inGutter(e)) {
            return;
        }
        e.consume();
        hover(card, false);
        card.toggleCollapsed();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!(e.getSource() instanceof Card card)) {
            return;
        }
        boolean on = inGutter(e);
        hover(card, on);
        card.setCursor(Cursor.getPredefinedCursor(
                on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        card.setToolTipText(on ? "Collapse" : null);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Fired when the pointer leaves the card OR moves onto one of its children;
        // both mean it is no longer on the strip, so the highlight must not stick.
        if (e.getSource() instanceof Card card) {
            hover(card, false);
        }
    }

    @Override public void mouseDragged(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
}
