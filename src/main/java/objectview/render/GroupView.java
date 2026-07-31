package objectview.render;

import objectview.Viewable;
import objectview.group.ViewableGroup;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Standalone compatibility browser composed from the pure {@link GroupTreeView}.
 * Applications embedding their own instance panel should use {@code GroupTreeView}
 * directly.
 */
public class GroupView extends JPanel {
    private final GroupTreeView treeView;
    private final Map<ViewableGroup<?>, CardListView> memberViews = new IdentityHashMap<>();

    public GroupView(ViewableGroup<?> rootGroup) {
        super(new BorderLayout());
        treeView = new GroupTreeView(rootGroup);
        treeView.setShowGroupHandler(this::showGroup);
        add(treeView, BorderLayout.CENTER);
    }

    private void showGroup(ViewableGroup<?> group) {
        if (group == null || group.getMembers().isEmpty()) return;
        CardListView view = memberViews.computeIfAbsent(group, selected -> {
            CardListView created = new CardListView();
            for (Viewable member : selected.getMembers()) created.addViewable(member);
            return created;
        });
        view.show(group.getFullName(), 2);
    }

    public JPanel getMainPanel() { return treeView; }
    public ViewableGroup<?> getRootGroup() { return treeView.getRootGroup(); }
    public JTree getTree() { return treeView.getTree(); }
    public JScrollPane getTreeScrollPane() { return treeView.getTreeScrollPane(); }
    public ViewableGroup<?> selectedGroup() { return treeView.selectedGroup(); }
    public ViewableGroup<?> getViewableGroup(DefaultMutableTreeNode node) {
        return treeView.getViewableGroup(node);
    }
    public void setShowGroupHandler(Consumer<ViewableGroup<?>> handler) {
        treeView.setShowGroupHandler(handler);
    }
    public void setSelectionHandler(Consumer<ViewableGroup<?>> handler) {
        treeView.setSelectionHandler(handler);
    }
    public JButton addControl(String label, Runnable action) {
        return treeView.addControl(label, action);
    }
    public void setStatusText(String text) { treeView.setStatusText(text); }

    public JFrame createFrame() {
        JFrame frame = new JFrame(getRootGroup().getDisplayName());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(this);
        frame.setSize(1200, 700);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        return frame;
    }

    public void showFrame() { createFrame().setVisible(true); }
}
