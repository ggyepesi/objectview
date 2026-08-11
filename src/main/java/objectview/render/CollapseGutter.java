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

    // Visible at rest, not only on hover: a control nobody can see is a control
    // nobody uses, and hover cannot advertise what the pointer has not found yet.
    private static final Color LINE = new Color(0, 80, 180, 150);
    private static final Color LINE_HOVER = new Color(0, 80, 180);
    private static final Color FILL = new Color(0, 80, 180, 20);
    private static final Color FILL_HOVER = new Color(0, 80, 180, 60);

    static final CollapseGutter INSTANCE = new CollapseGutter();

    /** The card whose gutter the pointer is currently over, or null. */
    private JComponent hovered;

    private CollapseGutter() { }

    /** Where each host keeps what its strip does. The action differs by host — a root
     *  card collapses itself, an expanded reference body collapses the chip that opened
     *  it — so the shared handler carries none of it. */
    private static final String ACTION = "objectview.collapseGutter.action";

    /**
     * Arms {@code host}'s gutter with the action that collapses it. Called only while
     * something is expanded — a collapsed thing has no gutter, so its whole width keeps
     * its normal behaviour.
     *
     * <p>Idempotent: a card rebuilt in place runs its build again, and a second
     * registration would toggle twice per press — collapsing and instantly re-expanding,
     * which looks like the strip doing nothing at all.
     */
    static void install(JComponent host, Runnable onCollapse) {
        host.putClientProperty(ACTION, onCollapse);
        for (java.awt.event.MouseListener existing : host.getMouseListeners()) {
            if (existing == INSTANCE) {
                return;
            }
        }
        host.addMouseListener(INSTANCE);
        host.addMouseMotionListener(INSTANCE);
    }

    /** Paints the strip, from the card's own paintComponent so it sits under the
     *  card's content and never intercepts a child's own painting. */
    void paint(Graphics g, JComponent card, int height) {
        boolean hot = card == hovered;
        g.setColor(hot ? FILL_HOVER : FILL);
        g.fillRect(0, 0, WIDTH, height);
        g.setColor(hot ? LINE_HOVER : LINE);
        g.fillRect(WIDTH / 2 - 1, 2, 2, Math.max(0, height - 4));
        // Caps at both ends: the line alone reads as a divider between the card and
        // whatever is left of it. Ends make it read as one object, the card's own.
        g.fillRect(2, 2, WIDTH - 4, 2);
        g.fillRect(2, Math.max(2, height - 4), WIDTH - 4, 2);
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
        if (!(e.getSource() instanceof JComponent host) || !inGutter(e)) {
            return;
        }
        if (!(host.getClientProperty(ACTION) instanceof Runnable collapse)) {
            return;
        }
        e.consume();
        hover(host, false);
        collapse.run();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!(e.getSource() instanceof JComponent host)) {
            return;
        }
        boolean on = inGutter(e);
        hover(host, on);
        host.setCursor(Cursor.getPredefinedCursor(
                on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        host.setToolTipText(on ? "Collapse" : null);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Fired when the pointer leaves the host OR moves onto one of its children;
        // both mean it is no longer on the strip, so the highlight must not stick.
        if (e.getSource() instanceof JComponent host) {
            hover(host, false);
        }
    }

    @Override public void mouseDragged(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
}
