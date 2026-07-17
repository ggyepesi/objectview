package objectview.utils.swing;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;

import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Small helpers for wrapping content in scroll panes, so toolbars/forms in
 * narrow panels (e.g. a split-pane side, the "Generated instances" view)
 * stay reachable instead of being clipped.
 */
public final class ScrollPaneUtils {

    private ScrollPaneUtils() {}

    /**
     * Wraps a component in a horizontal-only scroll pane: the content keeps
     * its natural width (nothing wraps or is clipped), and a horizontal
     * scrollbar appears only when the host is too narrow to show it all.
     *
     * <p>The wrapper reserves room for the scrollbar <i>only while it is
     * actually showing</i> — when everything fits, it is exactly as tall as
     * its content, so it never leaves a dead gap below a toolbar pinned to
     * {@code BorderLayout.NORTH}. It re-evaluates on resize.
     */
    public static JScrollPane horizontalOnly(JComponent content) {
        return new HorizontalOnlyScrollPane(content);
    }

    private static final class HorizontalOnlyScrollPane extends JScrollPane {
        private final JComponent content;
        private final int barHeight;

        HorizontalOnlyScrollPane(JComponent content) {
            super(content,
                    VERTICAL_SCROLLBAR_NEVER,
                    HORIZONTAL_SCROLLBAR_AS_NEEDED);
            this.content = content;
            setBorder(BorderFactory.createEmptyBorder());
            getHorizontalScrollBar().setUnitIncrement(24);
            this.barHeight = getHorizontalScrollBar().getPreferredSize().height;

            // When the host resizes, the overflow verdict (and therefore our
            // preferred height) may change, so ask the parent to re-lay-out.
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    revalidate();
                }
            });
        }

        // Whether the content is wider than the space we've been given, i.e.
        // a horizontal scrollbar is (or is about to be) needed. Before the
        // first layout getWidth() is 0; treat that as "fits" so we don't
        // reserve bar space pre-emptively.
        private boolean needsBar() {
            int width = getWidth();
            return width > 0 && content.getPreferredSize().width > width;
        }

        @Override
        public Dimension getPreferredSize() {
            int h = content.getPreferredSize().height + (needsBar() ? barHeight : 0);
            return new Dimension(0, h);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    }
}
