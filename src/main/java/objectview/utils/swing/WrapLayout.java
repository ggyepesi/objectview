package objectview.utils.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * {@link FlowLayout} that reports the height its rows ACTUALLY need.
 *
 * <p>FlowLayout wraps its content but computes a preferred size for a single row. Inside
 * a {@code BorderLayout.NORTH} — where the parent grants exactly the preferred height —
 * the wrapped rows are therefore laid out below the visible area and silently clipped.
 * A toolbar loses its right-hand end as the window narrows, and the user cannot tell
 * that controls exist at all: not a cramped layout, an invisible one.
 *
 * <p>This measures against the available width, so the reported height covers every row.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        // Signals to the parent that this row may shrink and re-wrap rather than
        // forcing the window to stay as wide as the widest single row.
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = availableWidth(target);
            Insets insets = target.getInsets();
            int horizontalInsets = insets.left + insets.right + getHgap() * 2;
            int maxWidth = targetWidth - horizontalInsets;

            Dimension size = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component component = target.getComponent(i);
                if (!component.isVisible()) {
                    continue;
                }
                Dimension componentSize = preferred
                        ? component.getPreferredSize() : component.getMinimumSize();
                if (rowWidth != 0 && rowWidth + getHgap() + componentSize.width > maxWidth) {
                    addRow(size, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += getHgap();
                }
                rowWidth += componentSize.width;
                rowHeight = Math.max(rowHeight, componentSize.height);
            }
            addRow(size, rowWidth, rowHeight);

            size.width += horizontalInsets;
            size.height += insets.top + insets.bottom + getVgap() * 2;

            // Inside a viewport a preferred width equal to the available width would
            // fight the scroll pane's own sizing pass; give back the extra gap.
            if (SwingUtilities.getAncestorOfClass(JScrollPane.class, target) != null
                    && target.isValid()) {
                size.width -= (getHgap() + 1);
            }
            return size;
        }
    }

    /** The width to wrap against: the container's own once laid out, else its parent's,
     *  falling back to unbounded so the first pass behaves like plain FlowLayout. */
    private static int availableWidth(Container target) {
        Container container = target;
        while (container.getSize().width == 0 && container.getParent() != null) {
            container = container.getParent();
        }
        int width = container.getSize().width;
        return width == 0 ? Integer.MAX_VALUE : width;
    }

    private void addRow(Dimension size, int rowWidth, int rowHeight) {
        size.width = Math.max(size.width, rowWidth);
        if (size.height > 0) {
            size.height += getVgap();
        }
        size.height += rowHeight;
    }
}
