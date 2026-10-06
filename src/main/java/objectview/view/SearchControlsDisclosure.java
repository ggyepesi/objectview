package objectview.view;

import objectview.render.ExpandablePanel;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;

/**
 * The shared disclosure surface for one Search/Sort/View panel.
 *
 * <p>When expanded in a short result area, the controls are capped at half of
 * the available height. The result list therefore remains visible and both
 * regions can use their own scrolling.
 */
public final class SearchControlsDisclosure extends JPanel {
    private final ExpandablePanel expandable;

    public SearchControlsDisclosure(
            JComponent controls, boolean initiallyExpanded) {
        super(new BorderLayout());
        if (controls == null) {
            throw new IllegalArgumentException("Search controls are required");
        }
        setOpaque(false);
        setMinimumSize(new Dimension(0, 0));
        expandable = new ExpandablePanel(
                initiallyExpanded,
                () -> header(false),
                () -> header(true),
                () -> controls);
        add(expandable, BorderLayout.CENTER);
    }

    public boolean isExpanded() {
        return expandable.isExpanded();
    }

    public void setExpanded(boolean expanded) {
        if (expandable.isExpanded() != expanded) expandable.toggle();
    }

    @Override public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        java.awt.Container parent = getParent();
        int available = parent == null ? 0 : parent.getHeight();
        if (available > 0) {
            preferred.height = Math.min(preferred.height,
                    Math.max(32, available / 2));
        }
        return preferred;
    }

    private static JLabel header(boolean expanded) {
        JLabel label = new JLabel((expanded ? "▼ " : "▶ ")
                + "Search / sort / view");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setToolTipText(expanded ? "Collapse controls" : "Expand controls");
        return label;
    }
}
