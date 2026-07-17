package objectview.virtual;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A lean replacement for {@code GridBagLayout} when stacking many cards in a few
 * columns. It lays out children in component order — column {@code i % cols}, row
 * {@code i / cols} — at the column width, with each row as tall as its tallest card.
 *
 * <p>The point is cost: {@code GridBagLayout.layoutContainer} rebuilds a grid-info
 * structure and runs a constraint/weight solve over <i>every</i> child on each pass,
 * which at tens of thousands of cards turns one {@code revalidate()} (e.g. expanding
 * a chip) into a multi-second freeze. This layout is plain arithmetic: O(n) with a
 * tiny constant, so a re-layout stays fast at any count. Reordering for sort is just
 * reordering the components (the layout follows component order) — no per-card
 * constraints to recompute.
 */
public final class CardStackLayout implements LayoutManager {

    private final int columns;
    private final int pad;   // outer margin
    private final int hgap;  // between columns
    private final int vgap;  // between rows

    public CardStackLayout(int columns) {
        this(columns, 8, 12, 16);
    }

    public CardStackLayout(int columns, int pad, int hgap, int vgap) {
        this.columns = Math.max(1, columns);
        this.pad = pad;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int columns() {
        return columns;
    }

    @Override public void addLayoutComponent(String name, Component comp) { }
    @Override public void removeLayoutComponent(Component comp) { }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Component[] cs = parent.getComponents();
            int colW = 0;
            for (Component c : cs) {
                if (c.isVisible()) {
                    colW = Math.max(colW, c.getPreferredSize().width);
                }
            }
            int rows = rowCount(cs);
            int height = 2 * pad;
            for (int r = 0; r < rows; r++) {
                height += rowHeight(cs, r);
                if (r < rows - 1) {
                    height += vgap;
                }
            }
            Insets in = parent.getInsets();
            int width = in.left + in.right + 2 * pad
                    + columns * colW + (columns - 1) * hgap;
            return new Dimension(width, in.top + in.bottom + height);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Component[] cs = parent.getComponents();
            Insets in = parent.getInsets();
            int avail = parent.getWidth() - in.left - in.right - 2 * pad
                    - (columns - 1) * hgap;
            int colW = Math.max(1, avail / columns);

            int rows = rowCount(cs);
            int y = in.top + pad;
            for (int r = 0; r < rows; r++) {
                int rowH = rowHeight(cs, r);
                for (int col = 0; col < columns; col++) {
                    int i = r * columns + col;
                    if (i >= cs.length) {
                        break;
                    }
                    Component c = cs[i];
                    if (!c.isVisible()) {
                        continue;
                    }
                    int x = in.left + pad + col * (colW + hgap);
                    c.setBounds(x, y, colW, rowH);
                }
                y += rowH + vgap;
            }
        }
    }

    private int rowCount(Component[] cs) {
        return (cs.length + columns - 1) / columns;
    }

    private int rowHeight(Component[] cs, int row) {
        int h = 0;
        for (int col = 0; col < columns; col++) {
            int i = row * columns + col;
            if (i < cs.length && cs[i].isVisible()) {
                h = Math.max(h, cs[i].getPreferredSize().height);
            }
        }
        return h;
    }
}
