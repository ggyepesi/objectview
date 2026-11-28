package objectview.search;

import javax.swing.Icon;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Graphics;

/** Marks an instance tab without changing its title, selection or contents. */
public final class SearchTabMarker {
    private SearchTabMarker() { }

    private static final Icon HIT = new Icon() {
        @Override public int getIconWidth() { return 10; }
        @Override public int getIconHeight() { return 10; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics dot = graphics.create();
            try {
                dot.setColor(component.getForeground());
                dot.fillOval(x + 2, y + 2, 6, 6);
            } finally {
                dot.dispose();
            }
        }
    };

    public static void mark(JTabbedPane tabs, Component page, boolean hasHits) {
        int index = tabs.indexOfComponent(page);
        if (index < 0) return;
        tabs.setIconAt(index, hasHits ? HIT : null);
        tabs.setToolTipTextAt(index, hasHits ? "This tab contains search matches" : null);
    }

    /** Bind a tab with one search engine, including asynchronous index publication. */
    public static void follow(JTabbedPane tabs, Component page, SearchPanel search) {
        search.addPropertyChangeListener(SearchPanel.HAS_SEARCH_HITS,
                event -> mark(tabs, page, search.hasSearchHits()));
        mark(tabs, page, search.hasSearchHits());
    }
}
