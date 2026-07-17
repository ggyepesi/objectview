package objectview.render;

import objectview.field.FieldProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Clickable header for a collapsible collection/map field — a ▶/▼ triangle and
 * "{@code fieldName (count)}" label. Left-click flips the collection's expand
 * state in {@link RenderContext} (keyed by the collection's identity)
 * and rebuilds the card, mirroring {@link ReferenceRow}: a collapsed
 * collection renders only this header, an expanded one renders the header plus
 * its items below.
 */
public class CollectionHeader extends JComponent {
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final int TRI_W = 12;

    private final String fieldName;
    private final int count;
    private final boolean expanded;
    private final Object key;
    private final boolean defaultExpanded;
    private final RenderContext renderContext;

    private boolean hover = false;

    public CollectionHeader(String fieldName,
                            List<String> fieldPath,
                            int count,
                            boolean expanded,
                            Object key,
                            boolean defaultExpanded,
                            RenderContext renderContext) {
        this.fieldName = fieldName == null ? "" : fieldName;
        this.count = count;
        this.expanded = expanded;
        this.key = key;
        this.defaultExpanded = defaultExpanded;
        this.renderContext = renderContext;

        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(expanded
                ? "Click to collapse (" + count + " items)"
                : "Click to expand (" + count + " items)");

        // Keep the field searchable even when collapsed.
        List<String> path = new ArrayList<>(fieldPath == null ? List.of() : fieldPath);
        putClientProperty(FieldProperties.FIELD_NAME_PROPERTY, this.fieldName);
        putClientProperty(FieldProperties.FIELD_PATH_PROPERTY, path);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (renderContext != null) {
                    renderContext.toggleCollectionExpanded(key, defaultExpanded);
                }
                refreshRootCard();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    private void refreshRootCard() {
        Card root = null;
        for (Container c = getParent(); c != null; c = c.getParent()) {
            if (c instanceof Card qp) {
                root = qp;
            }
        }
        if (root != null) {
            root.refresh();
        }
    }

    private Font labelFont() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return base.deriveFont(Font.BOLD);
    }

    private String text() {
        return fieldName + " (" + count + ")";
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(labelFont());
        int w = PAD_X + TRI_W + fm.stringWidth(text()) + PAD_X;
        int h = fm.getHeight() + 2 * PAD_Y;
        return new Dimension(Math.max(100, w), h);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(80, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Font font = labelFont();
            FontMetrics fm = g2.getFontMetrics(font);
            int baseline = PAD_Y + fm.getAscent();
            int triMid = baseline - fm.getAscent() / 2;

            g2.setColor(new Color(120, 120, 120));
            if (expanded) {
                g2.fillPolygon(
                        new int[]{PAD_X, PAD_X + 8, PAD_X + 4},
                        new int[]{triMid - 2, triMid - 2, triMid + 3},
                        3);
            } else {
                g2.fillPolygon(
                        new int[]{PAD_X + 1, PAD_X + 1, PAD_X + 6},
                        new int[]{triMid - 4, triMid + 4, triMid},
                        3);
            }

            g2.setFont(font);
            g2.setColor(hover ? new Color(0, 80, 180) : getForeground());
            g2.drawString(text(), PAD_X + TRI_W, baseline);
        } finally {
            g2.dispose();
        }
    }
}
