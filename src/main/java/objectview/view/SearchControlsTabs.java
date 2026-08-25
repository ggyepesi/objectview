package objectview.view;

import objectview.render.ExpandablePanel;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Cursor;

/**
 * One secondary Search/Sort/View workspace for several primary result panels.
 *
 * <p>Each {@link ViewableListPanel} keeps ownership of its controls and state; this
 * component only gives their stable external hosts a named tab and one shared
 * disclosure control. Result replacement therefore does not rebuild the surrounding
 * UI or reset a reader's active tab.
 */
public final class SearchControlsTabs extends JPanel {
    private final String title;
    private final JTabbedPane tabs = new JTabbedPane();
    private final ExpandablePanel expandable;

    public SearchControlsTabs(String title, boolean initiallyExpanded) {
        super(new BorderLayout());
        this.title = title == null || title.isBlank()
                ? "Search / sort / view" : title.trim();
        expandable = new ExpandablePanel(
                initiallyExpanded,
                () -> header(false),
                () -> header(true),
                () -> tabs);
        expandable.setMinimumSize(new java.awt.Dimension(0, 0));
        add(expandable, BorderLayout.CENTER);
    }

    public void addTab(String name, ViewableListPanel panel) {
        if (panel == null) throw new IllegalArgumentException("Result panel is required");
        tabs.addTab(name == null || name.isBlank() ? "Results" : name,
                panel.externalControls());
        expandable.refresh();
    }

    public boolean isExpanded() { return expandable.isExpanded(); }

    public void setExpanded(boolean expanded) {
        if (expandable.isExpanded() != expanded) expandable.toggle();
    }

    public int selectedIndex() { return tabs.getSelectedIndex(); }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < tabs.getTabCount()) tabs.setSelectedIndex(index);
    }

    private JComponent header(boolean expanded) {
        JLabel label = new JLabel((expanded ? "▼ " : "▶ ") + title
                + (tabs.getTabCount() == 0 ? "" : " (" + tabs.getTabCount() + ")"));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setToolTipText(expanded ? "Collapse controls" : "Expand controls");
        return label;
    }
}
